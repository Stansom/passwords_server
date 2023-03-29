(ns passman.db
  (:require [babashka.fs :as fs]
            [babashka.pods :as pods]
            [clojure.edn :as edn]
            [honey.sql :as sql]
            [passman.encryption :as encryption]
            [clojure.core :as c]))

(pods/load-pod 'org.babashka/postgresql "0.1.0")
(require '[pod.babashka.postgresql :as pg])

(def conf (edn/read-string (slurp "config.edn")))

(def db (or (:db conf) {:dbtype   "postgresql"
                        :host     "localhost"
                        :dbname   "postgres"
                        :user     "postgres"
                        :password ""
                        :port     5432}))

(defn delete-table! [db t]
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

(defn list-table
  "t => table name as keyword"
  [db t]
  (pg/execute! db (-> {:select [:*]
                       :from t} (sql/format))))
(-> {:create-table [:passwords :if-not-exists]
     :with-columns [[:id :serial :primary-key]
                    [:url :text [:not nil]]
                    [:username :text [:not nil]]
                    [:password :text [:not nil]]]}
    (sql/format))

(defn create-db! [db]
  (pg/execute! db (-> {:create-table [:passwords :if-not-exists]
                       :with-columns [[:id :serial :primary-key]
                                      [:url :text [:not nil]]
                                      [:username :text [:not nil]]
                                      [:password :text [:not nil]]
                                      [:login :text [:not nil]]]}
                      (sql/format)))
  (pg/execute! db (-> {:create-table [:users :if-not-exists]
                       :with-columns [[:id :serial :primary-key]
                                      [:username :text [:not nil]]
                                      [:password :text [:not nil]]
                                      [[:unique nil :username]]]}
                      (sql/format))))

(-> {:create-table [:passwords :if-not-exists]
     :with-columns [[:id :serial :primary-key]
                    [:url :text [:not nil]]
                    [:username :text [:not nil]]
                    [:password :text [:not nil]]
                    [:login :text [:not nil]]]}
    (sql/format))

(defn- add-id-column [db t]
  (pg/execute! db
               (-> {:alter-table [t]
                    :add-column [:id :serial :primary-key]}
                   (sql/format))))

(comment
  (-> {:create-table [:passwords :if-not-exists]
       :with-columns [[:id :serial :primary-key]
                      [:url :text [:not nil]]
                      [:username :text [:not nil]]
                      [:password :text [:not nil]]
                      [:login :text [:not nil]]]}
      (sql/format))
  (pg/execute! db
               (-> {:alter-table [:passwords]
                    :add-column [:login :text]}
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

(defn select-from [db t q]
  (pg/execute! db (-> {:select [:*]
                       :from [t]
                       :where [q]} (sql/format))))

(defn delete-from [db t clause]
  (pg/execute! db (-> {:delete-from [t]
                       :where [clause]} (sql/format))))

(defn find-password [db t u]
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
       (pg/execute! db (-> {:select [:p.login :p.url :p.password :p.id]
                            :from [[:users :u]]
                            :join [[:passwords :p] [:= :u.username :p.username]]
                            :where [:= :u.username u]} (sql/format)))))

(defn remove-entry [db id]
  (delete-from db :passwords [:= :id id]))

(defn update-password [db v id]
  (update-table db :passwords :password (encryption/encrypt-pass v) [:= :id id]))

(defn update-url [db v id]
  (update-table db :passwords :url v [:= :id id]))

(defn update-login [db v id]
  (update-table db :passwords :login v [:= :id id]))

(comment
  #_(delete-table db :users)
  #_(delete-table db :passwords)

  (list-passwords db "koha")

  (find-password db :users "koha")

  (insert-pass! db "koha" "rr.net" "kol" "kool")
  (delete-from db :passwords [:and [:= :username "koha"]
                              #_[:= :login "kol"]])

  (select-from db :passwords [:and [:= :username "h-test-user"]
                              #_[:= :login "kol"]])

  (insert-user db "oleg" "kluchiks1")

  (->> (list-passwords db "koha") (filter #(= "face.com" (:url %))))

  (insert-pass! db "koha" "face.com" "one" "bee")

  (remove-entry db 129)

  (update-password db "bemba" 175)
  (update-url db "hh.cm" 3)
  (update-login db "kuka" 3)

  (->> (list-table db :passwords) (filter #(= 3 (:passwords/id %))))
  (->> (list-table db :passwords) (filter #(= "koha" (:passwords/username %))) (filter #(= 135 (:passwords/id %))))

  #_(create-db! db))
