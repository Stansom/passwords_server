(ns dbs.postgres
  (:require
   [babashka.fs :as fs]
   [babashka.pods :as pods]
   [clojure.edn :as edn]
   [honey.sql :as sql]
   [passman.encryption :as encryption]))

(pods/load-pod 'org.babashka/postgresql "0.1.0")
(require '[pod.babashka.postgresql :as pg])

(def create-passwords-table-query  {:create-table [:passwords :if-not-exists]
                                    :with-columns [[:id :serial :primary-key]
                                                   [:url :text [:not nil]]
                                                   [:username :text [:not nil]]
                                                   [:password :text [:not nil]]
                                                   [:login :text [:not nil]]]})

(def create-users-table-query {:create-table [:users :if-not-exists]
                               :with-columns [[:id :serial :primary-key]
                                              [:username :text [:not nil]]
                                              [:password :text [:not nil]]
                                              [[:unique nil :username]]]})

(defn create-db! [db qrs]
  (when (seq qrs)
    (recur (pg/execute! db (-> (first qrs)
                               (sql/format))) (rest qrs))))

(defn insert-into [db t opts]
  (let [{:keys [columns values]} opts]
    (pg/execute! db (-> {:insert-into t
                         :columns columns
                         :values [values]
                         :on-conflict {:do-nothing true}} (sql/format)))))

(defn select-from [db t fs q]
  (pg/execute! db (-> {:select [fs]
                       :from [t]
                       :where [q]} (sql/format))))

(defn delete-from [db t clause]
  (pg/execute! db (-> {:delete-from [t]
                       :where [clause]} (sql/format))))