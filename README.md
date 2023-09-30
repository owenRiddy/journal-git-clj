# journal-git-clj

This is a script I developed to help me keep a journal when I write code.

A typical invocation in source form might be:

```
lein run --exclude-commits doc/commits.exclude --journal doc/real.journal >journaling-clojure.md
```

Alternatively, sometimes I build an uberjar (`lein uberjar`), rename it and run it with:

```
java -jar journal-git-clj.jar --exclude-commits doc/commits.exclude --journal doc/real.journal >journaling-clojure.md
```

Copyright © 2023 Owen Riddy

This program and the accompanying materials are made available under the
terms of the Eclipse Public License 2.0 which is available at
http://www.eclipse.org/legal/epl-2.0.

This Source Code may also be made available under the following Secondary
Licenses when the conditions for such availability set forth in the Eclipse
Public License, v. 2.0 are satisfied: GNU General Public License as published by
the Free Software Foundation, either version 2 of the License, or (at your
option) any later version, with the GNU Classpath Exception which is available
at https://www.gnu.org/software/classpath/license.html.
