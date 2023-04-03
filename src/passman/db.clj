(ns passman.db
  (:require
   [babashka.fs :as fs]
   [babashka.pods :as pods]
   [clojure.edn :as edn]
   [honey.sql :as sql]
   [passman.encryption :as encryption]))

(pods/load-pod 'org.babashka/postgresql "0.1.0")
(require '[pod.babashka.postgresql :as pg])

(pods/load-pod 'org.babashka/go-sqlite3 "0.1.0")
(require '[pod.babashka.go-sqlite3 :as sqlite])

(def conf (edn/read-string (slurp "config.edn")))

(def db (or (:db conf) {:dbtype   "postgresql"
                        :host     "localhost"
                        :dbname   "postgres"
                        :user     "postgres"
                        :password ""
                        :port     5432}))

(def dbs {"postgresql" {:create-db! 'dbs.postgres/hi
                        :insert-into 'dbs.postgres/send-hello}})

(comment
  @(resolve ((dbs "postgresql") :create-db!))
  ((resolve ((dbs "postgresql") :insert-into)) "sa")

  (resolve (symbol (str (dbs "postgresql")) "hi"))
  ((resolve (symbol (str (dbs "postgresql")) "send-hello"))))

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

(def db-commands {:execute! {"postgresql" pg/execute!
                             "sqlite" sqlite/execute!}
                  :query {"sqlite" sqlite/query
                          "postgresql" pg/execute!}})

(defn db-adapter [{dbtype :dbtype} command]
  (when-let [cmd (db-commands command)]
    (cmd dbtype)))

#_(defn delete-table! [db t]
    (pg/execute! db (-> {:drop-table [:if-exists t]} (sql/format))))

(defn update-table [db t k v clause]
  (pg/execute! db (-> {:update [t]
                       :set {k v}
                       :where [clause]} (sql/format))))

(defn insert-into
  "t    => table name as keyword
   opts => {
     :columns [vector of columns]
     :values  [vector of values]
   }"
  [db t opts]
  (let [{:keys [columns values]} opts]
    (pg/execute! db (-> {:insert-into t
                         :columns columns
                         :values [values]
                         :on-conflict {:do-nothing true}} (sql/format)))))

#_(defn list-table
    "t => table name as keyword"
    [db t]
    (pg/execute! db (-> {:select [:*]
                         :from t} (sql/format))))

(defmulti create-db! :dbtype)

(defmethod create-db! "sqlite"
  [{dbname :dbname}]
  (sqlite/execute! dbname (-> create-passwords-table-query
                              (sql/format)))
  (sqlite/execute! dbname (-> create-users-table-query
                              (sql/format)))
  #_(when-not (fs/exists? dbname)))

(defmethod create-db! "postgresql"
  [db]
  (pg/execute! db (-> create-passwords-table-query
                      (sql/format)))
  (pg/execute! db (-> create-users-table-query
                      (sql/format))))

(defmethod create-db! :default
  [db]
  (format "Create DB function is not implemented for %s DB type!" (:dbtype db)))

(comment
  (create-db! (assoc db :dbtype "sqlite"))

  (sqlite/query "postgres" (-> {:select [:url :password :rowid]
                                :from :passwords}
                               (sql/format)))

  (sqlite/query "postgres" (-> {:insert-into :passwords
                                :columns [:url :username :password :login]
                                :values [["url" "u2" "p2" "22lll.com"]]
                                :on-conflict {:do-nothing true}} (sql/format))))
#_(defn- add-id-column [db t]
    (pg/execute! db
                 (-> {:alter-table [t]
                      :add-column [:id :serial :primary-key]}
                     (sql/format))))

(defn insert-pass! [db user url login pass]
  (if (and (pos? (count url)) (pos? (count user)) (pos? (count pass)) (pos? (count login)))
    (let [hs (encryption/encrypt-pass pass)]
      (insert-into db
                   :passwords
                   {:columns [:url :username :password :login]
                    :values [url user hs login]}))
    {:err :url-user-pass-is-empty}))

(defn insert-user [db user pass]
  (when (and (seq user) (seq pass))
    (let [hs (encryption/encrypt-pass pass)]
      (insert-into
       db :users
       {:columns [:username :password]
        :values [user hs]}))))

(defn select-from
  ([db qry]
   (pg/execute! db (-> qry (sql/format))))
  ([db t fs q]
   (pg/execute! db (-> {:select [fs]
                        :from [t]
                        :where [q]} (sql/format)))))

(defn delete-from [db t clause]
  (pg/execute! db (-> {:delete-from [t]
                       :where [clause]} (sql/format))))

#_(defn find-password [db t u]
    (-> (pg/execute! db (-> {:select [:password]
                             :from [t]
                             :where [:= :username u]
                             :limit 1} (sql/format)))
        first
        (get (keyword (name t) "password"))))

(defn list-passwords [db u]
  (map (fn [e]
         (let [ep (encryption/decrypt-pass (:passwords/password e))]
           {:password ep
            :url (:passwords/url e)
            :login (:passwords/login e)
            :id (:passwords/id e)}))
       (select-from db {:select [:p.login :p.url :p.password :p.id]
                        :from [[:users :u]]
                        :join [[:passwords :p] [:= :u.username :p.username]]
                        :where [:= :u.username u]})))

(defn remove-entry [db id]
  (delete-from db :passwords [:= :id id]))

(defn update-password [db v id]
  (update-table db :passwords :password (encryption/encrypt-pass v) [:= :id id]))

(defn update-url [db v id]
  (update-table db :passwords :url v [:= :id id]))

(defn update-login [db v id]
  (update-table db :passwords :login v [:= :id id]))
