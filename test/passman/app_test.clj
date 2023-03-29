(ns passman.app-test
  (:require
   [clojure.string :as str]
   [clojure.test :refer [deftest is testing]]
   [passman.app :as a]
   [passman.system :as system]))

(def test-req {:remote-addr "0:0:0:0:0:0:0:1",
               :params {},
               :headers
               {"accept-encoding" "identity",
                "host" "localhost:3030",
                "user-agent" "python-urllib3/1.26.10"}
               :server-port 3030,
               :content-length 0,
               :websocket? false,
               :content-type nil,
               :character-encoding "utf8",
               :uri "/",
               :server-name "localhost",
               :query-string nil,
               :body nil,
               :scheme :http,
               :request-method :get})

(defn is-html? [s]
  (str/includes? s "<!DOCTYPE html>"))

(deftest app-system
  (testing "App is running and putting state into the system atom"
    (let [_ (a/stop-server)
          _ (a/run-server 2121)]
      (is (= (:port @system/system) 2121))
      (is (not (nil? (:server @system/system))))))

  (testing "Server stops"
    (a/stop-server)
    (a/run-server 2121)
    (is (= (:port @system/system) 2121))
    (a/stop-server)
    (is (= (:server @system/system) nil)))

  (testing "Server restarts"
    (when-not (nil? (:server @system/system)) (a/stop-server))
    (a/run-server 2121)
    (is (= (:port @system/system) 2121))
    (a/restart-server)
    (is (= (:port @system/system) 2121))
    (is (not (nil? (:server @system/system))))))

(deftest app-views
  (testing "Root view"
    (is (is-html? (:body (a/app test-req)))))
  (testing "List view"
    (is (= ":not-authorized" (:body (a/app
                                     (assoc test-req :uri "/view/list" :query-string "username=user1&password=password1")))))
    (testing "Register view"
      (is (is-html? (:body (a/app
                            (assoc test-req :uri "/view/register" #_#_:query-string "username=soma&password=pass")))))
      (is (str/includes? (:body (a/app
                                 (assoc test-req :uri "/view/register" #_#_:query-string "username=soma&password=pass"))) "Registration")))))

