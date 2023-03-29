(ns passman.app
  (:refer-clojure :exclude [list])
  (:require
   [clojure.pprint :as pprint]
   [clojure.tools.cli :refer [parse-opts]]
   [org.httpkit.server :as server]
   [passman.handlers :as hs]
   [passman.system :as system]
   [ruuter.core :as ruuter]
   [passman.routes :as rb]))

(def cli-options
  ;; An option with a required argument
  [["-p" "--port PORT" "Port"
    :default 8080
    :parse-fn #(Integer/parseInt %)
    :validate [#(< 0 % 65535)  "Must be a number from 0 to 65535"]]])

(parse-opts "" cli-options)

(def routes
  [{:path "/"
    :methods {:get (fn [req] (hs/dispatch {:type :root-view :payload req}))}}

   {:path "/logout"
    :methods {:get (fn [req] (hs/dispatch {:type :logout :payload req}))}}

   {:path "/register"
    :methods {:post (fn [req] (hs/dispatch {:type :register :payload req}))
              :get (fn [req] (hs/dispatch {:type :register-view :payload req}))}}

   {:path "/view/register"
    :methods {:get (fn [req] (hs/dispatch {:type :register-view :payload req}))}}

   {:path "/view/list"
    :methods {:get (fn [req] (hs/dispatch {:type :list-view :payload req}))}}

   {:path "/view/login"
    :methods {:get (fn [req] (hs/dispatch {:type :login :payload req}))}}

   {:path "/login"
    :methods {:post (fn [req] (hs/dispatch {:type :login-post :payload req}))}}

   {:path "/list"
    :methods {:get (fn [req] (hs/dispatch {:type :list-passwords :payload req}))}}

   {:path "/random"
    :methods {:get (fn [req] (hs/dispatch {:type :random-password :payload req}))}}

   {:path "/registered"
    :methods {:get (fn [req] (hs/dispatch {:type :user-registered :payload req}))}}

   {:path "/test"
    :methods {:get (fn [req] {:status 200
                              :body (with-out-str (pprint/pprint req  #_(:headers req)))})}}

   {:path "/add"
    :methods {:post (fn [req] (hs/dispatch {:type :add :payload req}))}}

   {:path "/update-pass"
    :methods {:patch (fn [req] (hs/dispatch {:type :update-password :payload req}))}}

   {:path "/update-url"
    :methods {:patch (fn [req] (hs/dispatch {:type :update-url :payload req}))}}

   {:path "/update-login"
    :methods {:patch (fn [req] (hs/dispatch {:type :update-login :payload req}))}}

   {:path "/remove-pass"
    :methods {:delete (fn [req] (hs/dispatch {:type :remove-password :payload req}))}}])

(defn app [req]
  (ruuter/route (rb/routes-builder routes) req))

(defn run-server [port]
  (let [s (server/run-server #'app
                             {:port port})]
    (println "Starting server on port: " port)
    (hs/dispatch {:type :create-db! :payload nil})
    (swap! system/system assoc :server s :port port)))

(defn stop-server []
  (when-some [s (:server @system/system)]
    (s)
    (swap! system/system assoc :server nil)))

(defn restart-server []
  (stop-server)
  (run-server (:port @system/system)))

(defn -main [& args]
  (let [{:keys [arguments options]} (parse-opts args cli-options)
        {:keys [port stop]} options]
    #_(println arguments options)
    (cond
      port (do (run-server port) @(promise))
      stop (stop-server)
      :else (println "nothing"))))

(comment
  (-main "--stop")
  (stop-server)
  (run-server 3030)
  @system/system
  (restart-server))