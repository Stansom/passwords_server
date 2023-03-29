(ns passman.db-test
  (:require
   [babashka.pods :as pods]
   [clojure.test :refer [deftest is testing]]
   [honey.sql :as sql]
   [passman.db :as db]))

(pods/load-pod 'org.babashka/postgresql "0.1.0")
(require '[pod.babashka.postgresql :as pg])

(def test-db {:dbtype   "postgresql"
              :host     "localhost"
              :dbname   "testdb"
              :port     5432})

(defn delete-table! [t]
  (pg/execute! test-db (-> {:drop-table [:if-exists t]} (sql/format))))

(defn table-exists? [t]
  (:tables/table_name (first (pg/execute! test-db [(format "SELECT table_name FROM information_schema.tables WHERE table_name='%s'" (name t))]))))

(comment

  (pg/execute! test-db (-> {:select [:u.username :p.url :p.password]
                            :from [[:users :u]]
                            :join [[:passwords :p] [:= :u.username :p.username]]
                            :where [:= :u.username "list"]} (sql/format)))

  (pg/execute! test-db ["create database testdb"])
  (pg/execute! test-db ["SELECT current_database()"])
  (pg/execute! test-db ["select version()"])
  (pg/execute! test-db ["SELECT * FROM pg_catalog.pg_tables"])
  (pg/execute! test-db ["SELECT * FROM users"])
  (pg/execute! test-db ["SELECT table_name FROM information_schema.tables WHERE table_name='passwords'"])

  (dotimes [n 10] (fn []
                    (vector (str n ".com") "list" (str n))))
  (pg/execute! test-db ["CREATE TABLE IF NOT EXISTS passwords (url TEXT NOT NULL, username TEXT NOT NULL, password TEXT NOT NULL)"])
  (:tables/table_name (first (table-exists? :passwords)))

  (table-exists? :passwords)
  (table-exists? :users)
  ;;
  )

(deftest db-test-queries
  (testing "Create passwords table query"
    (let [q (-> {:create-table [:passwords :if-not-exists]
                 :with-columns [[:id :serial :primary-key]
                                [:url :text [:not nil]]
                                [:username :text [:not nil]]
                                [:password :text [:not nil]]
                                [:login :text [:not nil]]]}
                (sql/format))
          _ (db/delete-table! test-db :passwords)
          _ (db/create-db! test-db)
          t  (table-exists? :passwords)]
      (is (not-empty t))
      (is (=
           ["CREATE TABLE IF NOT EXISTS passwords (id SERIAL PRIMARY KEY, url TEXT NOT NULL, username TEXT NOT NULL, password TEXT NOT NULL, login TEXT NOT NULL)"] q))))

  (testing "Create users table query"
    (let [q (-> {:create-table [:users :if-not-exists]
                 :with-columns [[:id :serial :primary-key]
                                [:username :text [:not nil]]
                                [:password :text [:not nil]]
                                [[:unique nil :username]]]}
                (sql/format))
          _ (db/delete-table! test-db :users)
          _ (db/create-db! test-db)
          t  (table-exists? :users)]
      (is (not-empty t))
      (is (=
           ["CREATE TABLE IF NOT EXISTS users (id SERIAL PRIMARY KEY, username TEXT NOT NULL, password TEXT NOT NULL, UNIQUE(username))"] q))))

  (testing "Password insertion"
    (let [q (-> {:insert-into :passwords
                 :columns [:url :username :password :login]
                 :values [["fb.com" "test-user" "test-pass" "test-login"]]
                 :on-conflict {:do-nothing true}} (sql/format))
          _ (db/delete-table! test-db :passwords)
          _ (db/create-db! test-db)
          _ (db/insert-pass! test-db "test-user" "fb.com" "test-login" "test-pass")
          s (first (db/select-from test-db :passwords [:= :username "test-user"]))]
      (is (= "test-user" (:passwords/username s)))
      (is (= "fb.com" (:passwords/url s)))
      (is (= "test-login" (:passwords/login s)))
      (is (= "ab74f925ee028d532a3cbda6cabe5635775670427bf8f3cad50c741cb70413d9" (:passwords/password s)))
      (is (= ["INSERT INTO passwords (url, username, password, login) VALUES (?, ?, ?, ?) ON CONFLICT DO NOTHING" "fb.com" "test-user" "test-pass" "test-login"] q))))

  (testing "User insertion"
    (let [q (-> {:insert-into :users
                 :columns [:username :password]
                 :values [["test-user" "test-pass"]]
                 :on-conflict {:do-nothing true}} (sql/format))
          _ (db/insert-user test-db "test-user" "test-pass")
          s (first (db/select-from test-db :users [:= :username "test-user"]))]
      (is (= "test-user" (:users/username s)))
      (is (= "ab74f925ee028d532a3cbda6cabe5635775670427bf8f3cad50c741cb70413d9" (:users/password s)))
      (is (= ["INSERT INTO users (username, password) VALUES (?, ?) ON CONFLICT DO NOTHING" "test-user" "test-pass"] q))))

  (testing "Table update"
    (let [_ (db/update-table test-db :users :username "updated-user-name" [:= :username "test-user"])
          s (first (pg/execute! test-db (-> {:select [:username :password]
                                             :from :users
                                             :where [:= :username "updated-user-name"]} (sql/format))))]
      (is (= "updated-user-name" (:users/username s)))))

  (testing "Find password"
    (let [_ (db/delete-table! test-db :users)
          _ (db/create-db! test-db)
          _ (db/insert-user test-db "find-pass-name" "found-pass")
          r (db/find-password test-db :users "find-pass-name")]

      (is (= "00ad7523cb2fca43b7c882b0bd78ecffdb24a853e62da7428e191b2585591c99" r))))

  (testing "Table deletion"
    (let [_ (db/create-db! test-db)
          _ (db/delete-table! test-db :users)
          r (table-exists? :users)]
      (is (= true (empty? r)))))

  (testing "Record deletion"
    (let [_ (db/delete-table! test-db :users)
          _ (db/create-db! test-db)
          _ (db/insert-user test-db "user-editing" "user-editing-pass")
          _ (db/delete-from test-db :users [:= :username "user-editing"])
          r (db/find-password test-db :users "user-editing")]
      (is (nil? r))))

  (testing "Passwords listing"
    (let [_ (db/delete-table! test-db :users)
          _ (db/delete-table! test-db :passwords)
          _ (db/create-db! test-db)
          _ (db/insert-user test-db "list" "list-pass")
          _ (doall (map (fn [n] (db/insert-pass! test-db "list" (str n ".com") (str "login-" n) (str n))) (range 10)))
          m (map (fn [n]
                   {:password (str n)
                    :url (str n ".com")
                    :login (str "login-" n)
                    :id (inc n)}) (range 10))

          r (db/list-passwords test-db "list")]
      (is (= m r))))
  (testing "Entry removing"
    (let [_ (db/delete-table! test-db :users)
          _ (db/delete-table! test-db :passwords)
          _ (db/create-db! test-db)
          _ (db/insert-user test-db "user-1" "user-pass")
          _ (db/insert-pass! test-db "user-1" "url-1" "login-1" "list-pass")
          e {:password "list-pass", :url "url-1", :login "login-1", :id 1}
          r (first (db/list-passwords test-db "user-1"))]
      (is (= e r))
      (db/remove-entry test-db 1)
      (is (empty? (first (db/list-passwords test-db "user-1"))))))

  (testing "Password updates"
    (let [_ (db/delete-table! test-db :users)
          _ (db/delete-table! test-db :passwords)
          _ (db/create-db! test-db)
          _ (db/insert-user test-db "user-1" "user-pass")
          _ (db/insert-pass! test-db "user-1" "url-1" "login-1" "list-pass")
          e {:password "list-pass", :url "url-1", :login "login-1", :id 1}
          r (first (db/list-passwords test-db "user-1"))]
      (is (= e r))
      (db/update-password test-db "new-pass" 1)
      (is (= {:password "new-pass", :url "url-1", :login "login-1", :id 1}
             (first (db/list-passwords test-db "user-1"))))))

  (testing "URL updates"
    (let [_ (db/delete-table! test-db :users)
          _ (db/delete-table! test-db :passwords)
          _ (db/create-db! test-db)
          _ (db/insert-user test-db "user-1" "user-pass")
          _ (db/insert-pass! test-db "user-1" "url-1" "login-1" "list-pass")
          e {:password "list-pass", :url "url-1", :login "login-1", :id 1}
          r (first (db/list-passwords test-db "user-1"))]
      (is (= e r))
      (db/update-url test-db "new-url" 1)
      (is (= {:password "list-pass", :url "new-url", :login "login-1", :id 1}
             (first (db/list-passwords test-db "user-1"))))))

  (testing "Login updates"
    (let [_ (db/delete-table! test-db :users)
          _ (db/delete-table! test-db :passwords)
          _ (db/create-db! test-db)
          _ (db/insert-user test-db "user-1" "user-pass")
          _ (db/insert-pass! test-db "user-1" "url-1" "login-1" "list-pass")
          e {:password "list-pass", :url "url-1", :login "login-1", :id 1}
          r (first (db/list-passwords test-db "user-1"))]
      (is (= e r))
      (db/update-login test-db "new-login" 1)
      (is (= {:password "list-pass", :url "url-1", :login "new-login", :id 1}
             (first (db/list-passwords test-db "user-1"))))))
;;
  )
;; (clojure.test/run-tests)
