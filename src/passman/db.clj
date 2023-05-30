(ns passman.db
  (:require
   [clojure.edn :as edn]
   [passman.encryption :as encryption]
   [passman.dbs.postgres :as pgs]
   [passman.dbs.sqlite :as sqlite]))

(def conf (edn/read-string (slurp "config.edn")))

(def db (or (:db conf) {:dbtype   "sqlite"
                        :host     "localhost"
                        :dbname   "passwords.db"
                        :port     5432}))

(def dbs {"postgresql" {:create-db! pgs/create-db!
                        :insert-into pgs/insert-into
                        :select-from pgs/select-from
                        :delete-from pgs/delete-from
                        :update-table pgs/update-table
                        :delete-table! pgs/delete-table!}
          "sqlite" {:create-db! sqlite/create-db!
                    :insert-into sqlite/insert-into
                    :select-from sqlite/select-from
                    :delete-from sqlite/delete-from
                    :update-table sqlite/update-table
                    :delete-table! sqlite/delete-table!}})

(defn run-command [{dbtype :dbtype} command]
  ((dbs dbtype) command)
  #_(resolve ((dbs dbtype) command)))

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

(defn delete-table! [db t]
  ((run-command db :delete-table!) db t))

(defn update-table [db t k v clause]
  ((run-command db :update-table) db t k v clause))

(defn insert-into
  "t    => table name as keyword
   opts => {
     :columns [vector of columns]
     :values  [vector of values]
   }"
  [db t opts]
  ((run-command db :insert-into) db t opts))

#_(defn list-table
    "t => table name as keyword"
    [db t]
    (pg/execute! db (-> {:select [:*]
                         :from t} (sql/format))))

(defn create-db! [db]
  ((run-command db :create-db!) db [create-passwords-table-query create-users-table-query]))

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
   (map (fn [m] (update-keys m
                             #(if (and (not (qualified-keyword? %)) (not (number? %)))
                                (let [qr (if (qry :join)
                                           (ffirst (qry :join))
                                           (:from qry))
                                      kw (name qr)]
                                  (keyword kw (name %)))
                                %))) ((run-command db :select-from) db qry)))
  ([db t fs q]
   (map (fn [m] (update-keys m
                             #(if (and (not (qualified-keyword? %)) (not (number? %)))
                                (let [kw (name t)]
                                  (keyword kw (name %)))
                                %))) ((run-command db :select-from) db t fs q))))

(defn delete-from [db t clause]
  ((run-command db :delete-from) db t clause))

(defn find-password [db t u]
  (-> (select-from db {:select [:password]
                       :from t
                       :where [:= :username u]
                       :limit 1})
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
