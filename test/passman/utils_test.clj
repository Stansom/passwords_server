(ns passman.utils-test
  (:require [clojure.test :refer [deftest is testing]]
            [passman.auth.auth :refer [auth-token auth-up]]
            [passman.db :as db]
            [passman.result :as result]
            [passman.utils.dates :refer [date-to-utc]]
            [passman.utils.queries-parser :refer [parse-multiple-query
                                                  parse-query]]
            [passman.utils.validate :refer [check-pers]]))

(def mock-req {:remote-addr "0:0:0:0:0:0:0:1",
               :params {},
               :headers
               {"accept-encoding" "identity",
                "host" "localhost:3030",
                "user-agent" "python-urllib3/1.26.10"
                "token" "8b5100074504c501f665b04879d0554700a6870d8c299d4590b0ea2c86aa2750"
                #_#_"Set-Cookie" '("username=b" "password=re")
                "cookie"
                #_"SLG_G_WPT_TO=ru; SLG_GWPT_Show_Hide_tmp=undefined; SLG_wptGlobTipTmp=undefined;"
                "SLG_G_WPT_TO=ru; 
                 SLG_GWPT_Show_Hide_tmp=undefined; 
                 SLG_wptGlobTipTmp=undefined; username=koha; 
                 password=miloha; 
                 token=8b5100074504c501f665b04879d0554700a6870d8c299d4590b0ea2c86aa2750;"}
               :server-port 3030,
               :content-length 0,
               :websocket? false,
               :content-type nil,
               :character-encoding "utf8",
               :uri "/",
               :server-name "localhost",
               :query-string "newpassword=;kliomsd&id=1" #_nil #_"username=koha&password=miloha&urlpassword=111new&url=111goo.com&login=jooh",
               :body nil #_(into-array Byte/TYPE "password=mgcz&username=kcz" #_"urlpassword=secret&url=oolo.com&login=jooh"),
               :scheme :http,
               :request-method :get})

(deftest queries-test
  (testing "Parse simple query"
    (let [q "username=johnny"]
      (is (= ["username" "johnny"] (parse-query q)))))

  (testing "Parse query with wrong query string"
    (let [q1 "username="
          q2 "username"
          q3 "=johnny"
          q4 "johnny"]
      (is (= ["username" nil] (parse-query q1)))
      (is (= ["username" nil] (parse-query q2)))
      (is (= ["" "johnny"] (parse-query q3)))
      (is (= ["johnny" nil] (parse-query q4)))))

  (testing "Parse multiple query"
    (let [q "username=user-test&password=pass-test&login=goog.com"
          r {:username "user-test", :password "pass-test", :login "goog.com"}
          p (parse-multiple-query q)]
      (is (result/ok? p))
      (is (= r (:ok p)))))

  (testing "Parse multiple query, empty query"
    (let [q "username=&password=pass-test&login="
          r {:username nil, :password "pass-test", :login nil}
          p (parse-multiple-query q)]
      (is (result/ok? p))
      (is (= r (:ok p)))))

  (testing "Parse multiple query, wrong query"
    (let [q "=&password=pass-test&login="
          r {nil nil, :password "pass-test", :login nil}
          p (parse-multiple-query q)]
      (is (result/ok? p))
      (is (= r (:ok p)))))

  (testing "If all keys presented run function, otherwise spit an error"
    (let [q (:ok (parse-multiple-query "username=user-test&password=pass-test&login=goog.com"))
          {:keys [username password login]} q
          p (check-pers [username password login] (constantly {:ok :is-ok}))]
      (is (result/ok? p))))

  (testing "If all keys presented run function, otherwise spit an error"
    (let [q (:ok (parse-multiple-query "=&password=pass-test&login="))
          {:keys [username password login]} q
          p (check-pers [username password login] (constantly {:ok :is-ok}))]
      (is (result/err? p))))

;;
  )

(deftest time-test
  (testing "Instant to UTC time zone"
    (let [inst (java.time.Instant/parse "2022-01-25T13:59:47.052581Z")
          eq "Tue, 25 Jan 2022 13:59:47 Z"
          upc (date-to-utc inst)]
      (is (= eq upc)))))

(deftest handlers-test
  (testing "Auth"
    (is (result/ok? (auth-token
                     (assoc-in mock-req  [:headers "token"] "8b5100074504c501f665b04879d0554700a6870d8c299d4590b0ea2c86aa2750"))))
    (is (result/err? (auth-token mock-req))))

  (testing "User/password auth"
    (let [_ (db/insert-user db/db "h-test-user" "h-pass")
          mc (assoc mock-req :query-string
                    (format "username=%s&password=%s" "h-test-user" "h-pass"))]

      (is (= true (result/ok? (auth-up mc))))
      (is (result/err? (auth-up {:err {:query-string ""}}))))))