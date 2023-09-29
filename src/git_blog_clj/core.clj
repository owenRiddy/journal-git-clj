(ns git-blog-clj.core
  (:gen-class)
  (:require
   [clj-jgit.porcelain :as gp]
   [clj-jgit.querying :as gq]
   [clojure.java.io :as io]
   [clojure.string :as string]
   [clojure.tools.cli :refer [parse-opts]]))

(def r
  "
  This repo, the one for this project. The local `.git` in the same root as
  `project.clj`
  "
  (gp/load-repo ".git"))

(def skip-commits
  #{"5ec8855d6c3455087eb556578a07892f8005ad94"})

(defn banned-sha1-hash?
  [^org.eclipse.jgit.revwalk.RevCommit rev]
  (->> rev
       .getName
       (contains? skip-commits)))

(defn to-diff-block
  [idx file]
  (->>
   (concat ["" (str "# Commit " (inc idx)) "```diff"] file ["````" "" ""])
   (into [])))

(defn repo-data
  [r]
  (->>
   (gq/rev-list r)
   (remove banned-sha1-hash?)
   reverse
   (map gq/changed-files-with-patch (repeat r))
   ;; `gq/changed-files-with-patch` does not return strings, it evaluates to
   ;; some sort of quasi-string object that breaks split-lines, somehow. Java
   ;; folk, at it again with their wacky ideas!
   (map str)
   (filter seq)
   (map string/split-lines)
   (map-indexed to-diff-block)
   flatten))

(defn output
  [markup-data repo-data]
  (loop [acc
         []

         state ; free in-diff
         :free

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
        (recur (conj acc m') :free m+ r)

        ;; Case 2; We've consumed m -> keep consuming r
        (empty? m)
        (recur (conj acc r') :free m r+)

        ;; Case 3; we're adding lines from the markup file until we find a new thing to match on.
        (not matchable-m?)
        (recur
         (conj acc m') state m+ r)

        ;; Case 4; we're looking for a match and find one. Since we matched a
        ;; line of code we must be interrupting a diff.
        (= r' (string/replace-first m' "|" ""))
        (recur (into acc [r' "```"]) :in-diff m+ r+)

        ;; Case 5; we're waiting for a match but can't possibly find it. Dump
        (empty? r)
        (recur (conj acc m') state m+ r)

        ;; Case 6; we're waiting for a match and don't see it yet.
        :else
        (let [new-items
              (if (= state :in-diff)
                ["" "```diff" r']
                [r'])]
          (recur (into acc new-items) :free m r+))))))

(def cli-options
  [[nil "--exclude-commits FILE" "File of commit hashes (one per line) to exclude from the markdown generated."]
   [nil "--repo PATH" "Path to the root of your git repo"]
   [nil "--journal FILE" "The journal file"]
   ["-h" "--help"]])

(defn -main
  "I don't do a whole lot ... yet."
  [& args]
  (let [{:keys [options summary]}
        (parse-opts args cli-options)

        journal-data
        (if (:journal options)
          (->> options
               :journal
               io/reader
               line-seq)
          [])]

    (if (:help options)
      (do
        (println "Options:")
        (println summary))

      (->>
       (repo-data r)
       (output journal-data)
       (interpose "\n")
       (apply str)
       println))))
