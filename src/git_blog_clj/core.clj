(ns git-blog-clj.core
  (:gen-class)
  (:require
   [clj-jgit.porcelain :as gp]
   [clj-jgit.querying :as gq]
   [clojure.java.io :as io]
   [clojure.string :as string]
   [clojure.tools.cli :refer [parse-opts]])
  (:import
   [org.eclipse.jgit.api Git]
   [org.eclipse.jgit.revwalk RevCommit]))

(defn banned-sha1-hash?
  [skip-commits ^org.eclipse.jgit.revwalk.RevCommit rev]
  (->> rev
       .getName
       (contains? skip-commits)))

(defn revcommit->lines
  [^Git repo idx ^RevCommit rev-commit]
  (let [commit-text
        (->>
         rev-commit
         (gq/changed-files-with-patch repo)
         ;; `gq/changed-files-with-patch` does not return strings, it evaluates to
         ;; some sort of quasi-string object that breaks split-lines, somehow. Java
         ;; folk, at it again with their wacky ideas!
         str)]

    (if (seq commit-text)
      (as-> commit-text $
        (string/split-lines $)
        (concat [""
                 (str "# Commit " (inc idx))
                 "```diff"]
                $
                ["```"
                 (str "> Commit hash " (.getName rev-commit))
                 ""
                 ""])
        (into [] $))
      [])))

(defn repo-data
  [r skip-commits]
  (->>
   (gq/rev-list r)
   (remove (partial banned-sha1-hash? skip-commits))
   reverse
   rest ; diff of first commit is ""
   (map-indexed (partial revcommit->lines r))
   flatten))

(defn partition-when
  "
  Breaks a coll up into partitions, starting a new partition for each item where
  `pred?` is `true`
  "
  [pred? coll]
  (cond
    (empty? coll)
    []

    (-> coll count (= 1))
    [(vec coll)]

    :else
    (let [partition-when'
          (fn [acc itm]
            (if (pred? itm)
              (conj acc [itm])
              (conj (vec (butlast acc)) (conj (last acc) itm))))]
      (reduce partition-when' [[(first coll)]] (rest coll)))))

(defn journal-repo-merge
  "Takes the journal file, and a custom data structure (as lines of text)"
  [markup-data repo-data]
  (loop [acc
         []

         state ; free in-diff
         :free

         ;; Break journal up into blocks, where the first line of the block is what is to be matched
         ;; Eg: (= m' ["|match me" "Comments" "Other comments"])
         ;; Unfortunately this means m' and r' are different things.
         [m' & m+ :as m]
         (partition-when #(= (first %) \|) markup-data)

         [r' & r+ :as r]
         repo-data]
    (let [;; Only use this if we consume from r
          new-state
          (cond
            (= r' "```diff")
            :in-diff

            (= r' "```")
            :free

            :else
            state)]
      (cond
        ;; Case 0; we're finished. Return the accumulator
        (and (empty? r) (empty? m))
        acc

        ;; Case 1; We've consumed r -> keep consuming m
        (empty? r)
        (recur (into acc (rest m')) state m+ r)

        ;; Case 2; We've consumed m -> keep consuming r
        (empty? m)
        (recur (conj acc r') new-state m r+)

        ;; Case 3; we're in the introductory matter
        (-> m' ffirst (not= \|))
        (recur (into acc m') state m+ r)

        ;; Case 4; we're looking for a match and find one.
        (-> m' first (string/replace-first "|" "") (= r'))
        (if (= state :in-diff)
          (recur (into acc (concat [r' "```"] (rest m') ["```diff"])) new-state m+ r+)
          (recur (into acc (concat [r'] (rest m'))) new-state m+ r+))

        ;; Case 5; we're looking for a match and don't see it yet.
        :else
        (recur (conj acc r') new-state m r+)))))

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
       (journal-repo-merge journal-data)
       (interpose "\n")
       (apply str)
       println))))
