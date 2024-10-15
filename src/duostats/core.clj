(ns duostats.core
  (:gen-class)
  (:require [clj-http.client :as http]
            [cheshire.core :as json]
            [cprop.core :refer [load-config]]
            [clojure.java.io :as io]
            [clojure.pprint :as pp]
            [clojure.edn :as edn]
            [clojure.tools.cli :refer [parse-opts]])
  (:import java.util.Base64))

(def user-agent "Mozilla/5.0 (X11; Linux x86_64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/83.0.4103.116 Safari/537.36")
;; (def duolingo-login-url "https://www.duolingo.com/api/1/login")
(def duolingo-login-url "https://www.duolingo.com//login")
(def duolingo-user-url "https://www.duolingo.com/2017-06-30/users?username=")

(def cookie-jar (clj-http.cookies/cookie-store))

(defn home-directory []
  (System/getProperty "user.home"))

(defn config-file []
  (io/file (home-directory) ".duostatsrc"))

(defn get-config []
  (if (.exists (config-file))
    (load-config :file (config-file))
    (load-config)))

(def configuration (delay (get-config)))

;; TODO: should come from config file -wcp14/10/24
(defn jwt []
  (:jwt @configuration))

(defn http-headers []
  {"Authorization" (str "Bearer " (jwt))
   "User-Agent" user-agent})

(defn http-get [url]
  (http/get url
            {:headers (http-headers)
             :cookie-store cookie-jar}))

(defn http-post [url data]
  (http/get url
            {:headers (http-headers)
             :accept :json
             :content-type :json
             :body (json/generate-string data)
             :cookie-store cookie-jar}))

(defn make-jwt-cookie [value]
  (clj-http.cookies/to-basic-client-cookie ["jwt_token" {:value value}]))

(defn add-jwt-cookie [value]
  (clj-http.cookies/add-cookie cookie-jar (make-jwt-cookie value)))

(comment
  (add-jwt-cookie (jwt)))

(defn login [username password]
  (let [response (http/post duolingo-login-url
                            {:body (json/encode {:login username
                                                 :password password})
                             :form-params {:login username
                                           :password password}
                             :content-type :json
                             :accept :json
                             :headers {"User-Agent" user-agent}
                             :cookie-store cookie-jar})]
    response
    
    #_(if (= 200 (:status response))
      (get-in response [:body #_:jwt])
      (throw (Exception. "Login failed")))))

(defn get-version-info []
  (-> (http-get "https://www.duolingo.com/api/1/version_info")
      :body
      (json/parse-string keyword)))

(comment
  (get-version-info))

(defn decode-base64 [s]
  (String. (.decode (Base64/getDecoder) s)))

(defn jwt-decode [jwt]
  (let [[header payload signature] (clojure.string/split jwt #"\.")
        dec-b64 (comp #(json/decode % keyword)
                      decode-base64)]
    {:header (dec-b64 header)
     :payload (dec-b64 payload)
     :signature signature}))

(defn jwt-uuid [jwt]
  (get-in (jwt-decode jwt) [:payload :sub]))

(defn get-user [user-id]
  (-> (http-get (str "https://www.duolingo.com/2017-06-30/users/"
                     user-id))
      :body
      (json/decode keyword)
      #_:username))

(defn get-user-light [user-id]
  (-> (get-user user-id)
      (dissoc :experiments :rewardBundles :currentCourse :currencyRewardBundles)))

(defn user-id []
  (or (:user-id @configuration)
      (jwt-uuid (jwt))))

(def user-stats (delay (get-user (user-id))))

(comment
  (:username @user-stats)
  (:totalXp @user-stats)
  (:streak @user-stats)
  (:courses @user-stats)
  (get-user-light (user-id)))

(defn user-courses [user-stats]
  (->> (:courses user-stats)
       (reduce (fn [m c]
                 (assoc m (:learningLanguage c) c))
               {})))

(comment
  (user-courses @user-stats))

(defn stats-file []
  (or (:stats-file @configuration)
      (io/file (home-directory) ".duostats.db")))

(defn now []
  (java.time.LocalDateTime/now))

(defn make-data-point [user-stats]
  {:courses (->> (user-courses user-stats)
                 (map (fn [[k v]]
                        [k (select-keys v [:title :xp])]))
                 (into {}))
   :time (str (now))})

(comment
  (make-data-point @user-stats))

(defn save-user-xps [user-stats]
  (let [xps (make-data-point user-stats)]
    (when xps
      (with-open [out (io/writer (stats-file) :append true)]
        (binding [*out* out]
          (pp/pprint xps))))))

(defn ignoring-errors [f & args]
  (try
    (apply f args)
    (catch Exception _
      nil)))

(defn read-data-point [in]
  (edn/read {:eof nil} in))

(defn load-data-points-from-db []
  (with-open [in (java.io.PushbackReader. (io/reader (stats-file)))]
    (->> (repeatedly #(read-data-point in))
         (take-while some?)
         doall)))

(defn list-progress [data-points]
  (->> (partition 2 1 data-points)
       (map (fn [[prev this]]
              (update this :courses
                      (fn [courses]
                        (->> (map (fn [[k v]]
                                    (let [prev-course (get (:courses prev) k)]
                                      [k (assoc v :progress
                                                (if prev-course
                                                  (- (:xp v) (:xp prev-course))
                                                  (:xp v)))]))
                                  courses)
                             (remove nil?)
                             (into {}))))))))

(comment
  (update-db)
  (list-progress (load-data-points-from-db)))

(defn print-progress-table [data-points]
  (->>  data-points
        (map (fn [dp]
               (reduce (fn [m [_ course]]
                         (assoc m (:title course) (:progress course)))
                       {:time (:time dp)}
                       (:courses dp))))
        pp/print-table))

(comment
  (print-progress-table (list-progress (load-data-points-from-db))))

(defn update-db []
  (let [user-stats (get-user (user-id))]
    (save-user-xps user-stats)))

(def cli-options
  [["-u" "--update" "Fetch latest stats and update the duo db"]
   ["-p" "--progress" "Read the duo db and print the progress table"]
   ["-v" nil "Verbosity level"
    :id :verbosity
    :default 0
    :update-fn inc]
   ["-h" "--help"]])

(defn exit [code]
  (System/exit code))

(defn usage [summary errors]
  (run! println errors)
  (println summary)
  (exit -1))

(defn print-progress []
  (print-progress-table (list-progress (load-data-points-from-db))))

(defn -main [& args]
  (let [{:keys [options arguments summary errors]} (parse-opts args cli-options)]
    (cond errors
          (usage summary errors)

          (:update options)
          (update-db)

          (:progress options)
          (print-progress)

          :else
          (usage summary ["Either -u or -p must be specified"]))
    (exit 0)))


