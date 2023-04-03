(ns passman.dbs.sqlite
  (:require
   [babashka.pods :as pods]
   [honey.sql :as sql]))

(pods/load-pod 'org.babashka/go-sqlite3 "0.1.0")
(require '[pod.babashka.go-sqlite3 :as sqlite])

(defn create-db! [{:keys [:dbname] :as db}  qrs]
  (when (seq qrs)
    (recur (let [cl (map
                     #(if (some #{:id} %) [:id :integer :primary-key] %)
                     (:with-columns (first qrs)))
                 dl (assoc (first qrs) :with-columns cl)]
             (sqlite/execute! dbname (-> dl
                                         sql/format)) db) (rest qrs))))

(defn insert-into [{dbname :dbname} t opts]
  (let [{:keys [columns values]} opts]
    (sqlite/query dbname (-> {:insert-into t
                              :columns columns
                              :values [values]
                              :on-conflict {:do-nothing true}} (sql/format)))))

(defn update-table [{dbname :dbname} t k v clause]
  (sqlite/query dbname (-> {:update [t]
                            :set {k v}
                            :where [clause]} (sql/format))))

(defn select-from
  ([{dbname :dbname} qry]
   (sqlite/query dbname (-> qry (sql/format))))
  ([{dbname :dbname} t fs q]
   (sqlite/query dbname (-> {:select fs
                             :from [t]
                             :where [q]} (sql/format)))))

(defn delete-from [{dbname :dbname} t clause]
  (let [[f s th] clause
        dcl (if (= s :id) [f :rowid th] clause)]
    (sqlite/query dbname (-> {:delete-from [t]
                              :where [dcl]} (sql/format)))))

(defn delete-table! [{dbname :dbname} t]
  (sqlite/execute! dbname (-> {:drop-table [:if-exists t]} (sql/format))))
