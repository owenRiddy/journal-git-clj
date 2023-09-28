(ns git-blog-clj.core
  (:gen-class)
  (:require
   [clj-jgit.porcelain :as gp]
   [clj-jgit.querying :as gq]
   [clojure.string :as string]))

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

(def repo-data
  (->>
   (map gq/changed-files-with-patch (repeat r) (gq/rev-list r))
   (interpose "\n\n\n")
   (apply str)
   string/split-lines))

(spit "my-text.txt"
      "Nothing\nYet\n|   :main ^:skip-aot git-blog-clj.core\nHehe")

(def markup-data
  (->> (slurp "my-text.txt")
       string/split-lines))

(defn output
  [markup-data repo-data]
  (loop [acc
         []

         m
         markup-data

         r
         repo-data]
    (let [[m' & m+]
          m

          [r' & r+]
          r

          matchable-m?
          (= (first m') \|)]
      (cond
        ;; Case 0; we're finished. Return the accumulator
        (and (empty? r) (empty? m))
        acc

        ;; Case 1; We've consumed r -> keep consuming m
        (empty? r)
        (recur (conj acc m') m+ r)

        ;; Case 2; We've consumed m -> keep consuming r
        (empty? m)
        (recur (conj acc r') m r+)

        ;; Case 3; we're adding lines from the markup file until we find a new thing to match on.
        (not matchable-m?)
        (recur
         (conj acc m') m+ r)

        ;; Case 4; we're looking for a match and find one.
        (= r' (string/replace-first m' "|" ""))
        (recur (conj acc r') m+ r+)

        ;; Case 5; we're waiting for a match but can't possibly find it. Dump
        (empty? r)
        (recur (conj acc m') m+ r)

        ;; Case 6; we're waiting for a match and don't see it yet.
        :else
        (recur (conj acc r') m r+)))))

(spit "out.txt"
      (->>
       (output markup-data repo-data)
       (interpose "\n")
       (apply str)))
