# duostats

Fetch Duolingo user data and display XP progress.

## Installation

   $ lein uberjar

Before you start using this program, you need to create a
configuration file in `~/.duostatsrc`.  The file should at the very
least contain a JWT token like the following:

```
{:jwt "this is where your JWT token is going to be"}
```

As of today October 2024, there is no way to get a JWT (none I'm aware
of) through the Duolingo API.  To get the JWT token you need to login
manually with your browser and dig in the cookies exchanged between
the browser and the server.  With Firefox this is done with the
Inspector.  You should notice a `jwt_token`.  That's the one you
should put in your configuration file.`

## Usage

The program runs in two modes, that can be chosen with the `-p` option
or the `-u` option.  The `-u` option updates the local DB with the
latest XP data.  The `-p` option prints a table of progress.

    $ java -jar duostats-0.1.0-STANDALONE.jar [args]

## Options

  * `-p` print a summary table of the user progress in his studies.
  * `-u` fetch the latest user data and update the local DB

## Examples

...

## License

Copyright © 2024 FIXME

This program and the accompanying materials are made available under the
terms of the Eclipse Public License 2.0 which is available at
http://www.eclipse.org/legal/epl-2.0.

This Source Code may also be made available under the following Secondary
Licenses when the conditions for such availability set forth in the Eclipse
Public License, v. 2.0 are satisfied: GNU General Public License as published by
the Free Software Foundation, either version 2 of the License, or (at your
option) any later version, with the GNU Classpath Exception which is available
at https://www.gnu.org/software/classpath/license.html.
