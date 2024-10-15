(defproject duostats "0.1.0-SNAPSHOT"
  :description "FIXME: write description"
  :url "http://example.com/FIXME"
  :license {:name "EPL-2.0 OR GPL-2.0-or-later WITH Classpath-exception-2.0"
            :url "https://www.eclipse.org/legal/epl-2.0/"}
  :dependencies [[org.clojure/clojure "1.12.0"]
                 [clj-http "3.13.0"]
                 [cheshire "5.13.0"]
                 [cprop "0.1.20"]
                 [org.clojure/tools.cli "1.1.230"]
                 #_[com.github.sikt-no/clj-jwt "0.5.98"]]
  :main ^:skip-aot duostats.core
  :target-path "target/%s"
  :profiles {:uberjar {:aot :all
                       :jvm-opts ["-Dclojure.compiler.direct-linking=true"]}
             :dev {:resource-paths ["dev-resources" "resources"]}})
