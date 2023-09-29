(ns git-blog-clj.core
  (:gen-class)
  (:require
   [clj-jgit.porcelain :as gp]
   [clj-jgit.querying :as gq]
   [clojure.java.io :as io]
   [clojure.string :as string]
   [clojure.tools.cli :refer [parse-opts]]))

(defn banned-sha1-hash?
  [skip-commits ^org.eclipse.jgit.revwalk.RevCommit rev]
  (->> rev
       .getName
       (contains? skip-commits)))

(defn to-diff-block
  [idx file]
  (->>
   (concat ["" (str "# Commit " (inc idx)) "```diff"] file ["````" "" ""])
   (into [])))

(defn repo-data
  [r skip-commits]
  (->>
   (gq/rev-list r)
   (remove (partial banned-sha1-hash? skip-commits))
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

        skip-commits
        (try (->> options :exclude-commits io/reader line-seq (reduce conj #{}))
             (catch Exception _e #{}))

        repo
        (try (-> options :repo (str ".git") gp/load-repo (repo-data skip-commits))
             (catch Exception _e []))

        journal-data
        (try
          (->> options
               :journal
               io/reader
               line-seq)
          (catch Exception _e []))]

    (if (:help options)
      (do
        (println "Options:")
        (println summary))

      (->>
       repo
       (output journal-data)
       (interpose "\n")
       (apply str)
       println))))
