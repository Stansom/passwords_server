(ns passman.dbs.postgres
  (:require
   [babashka.pods :as pods]
   [honey.sql :as sql]))

(pods/load-pod 'org.babashka/postgresql "0.1.0")
(require '[pod.babashka.postgresql :as pg])

(defn create-db! [db qrs]
  (when (seq qrs)
    (println (first qrs))
    (recur (do
             (pg/execute! db (-> (first qrs)
                                 sql/format)) db) (rest qrs))))

(defn insert-into [db t opts]
  (let [{:keys [columns values]} opts]
    (pg/execute! db (-> {:insert-into t
                         :columns columns
                         :values [values]
                         :on-conflict {:do-nothing true}} (sql/format)))))

(defn update-table [db t k v clause]
  (pg/execute! db (-> {:update [t]
                       :set {k v}
                       :where [clause]} (sql/format))))

(defn select-from
  ([db qry]
   (pg/execute! db (-> qry (sql/format))))
  ([db t fs q]
   (pg/execute! db (-> {:select fs
                        :from [t]
                        :where [q]} (sql/format)))))

(defn delete-from [db t clause]
  (pg/execute! db (-> {:delete-from [t]
                       :where [clause]} (sql/format))))

(defn delete-table! [db t]
  (pg/execute! db (-> {:drop-table [:if-exists t]} (sql/format))))
