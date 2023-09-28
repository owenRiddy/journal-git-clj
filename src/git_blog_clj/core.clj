(ns git-blog-clj.core
  (:gen-class)
  (:require
   [clj-jgit.internal :as gi]
   [clj-jgit.porcelain :as gp]
   [clj-jgit.querying :as gq]))

(defn -main
  "I don't do a whole lot ... yet."
  [& _args]
  (println "Hello, World!"))

(def r
  "
  This repo, the one for this project. The local `.git` in the same root as
  `project.clj`
  "
  (gp/load-repo ".git"))

(gi/resolve-object r "26ced4b9468d769a347102684e6b5513ee0d37a7")

(println
 (gq/changed-files-with-patch
  r
  (second
   ;; Turns out to be a poor man's rev-list
   (keys
    (gq/build-commit-map r
                         (gi/new-rev-walk r))))))

(run! println
      (map gq/changed-files-with-patch (repeat r) (gq/rev-list r)))
