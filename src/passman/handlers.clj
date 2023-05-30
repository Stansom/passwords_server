(ns passman.handlers
  (:require [babashka.json :as json]
            [clojure.core :as c]
            [passman.auth.auth :refer [already-logged? auth-body auth-token
                                       auth-up auth?]]
            [passman.cookies.cookies :refer [parse-cookie save-token-to-cookie]]
            [passman.db :as db]
            [passman.passgen :as gen]
            [passman.result :as result]
            [passman.system :as system]
            [passman.utils.http-errors :refer [errors]]
            [passman.utils.queries-parser :refer [parse-multiple-query
                                                  parse-post-body parse-query]]
            [passman.view :as view]))

(defn root-view
  "Handles root view path"
  [req]
  {:status 200
   :body (view/root req)})

(defn list-view
  "Handles list view path"
  [req]
  (-> req
      (result/flat-map-ok
       (fn [r]
         (when-let [u (r :username)]
           {:status 200
            :body (view/list-passwords u
                                       (db/list-passwords db/db u))})))
      (result/flat-map-err
       (constantly (errors :auth-error)))))

(defn list-passwords
  "Handles list passwords path, returns json"
  [req]
  (-> req
      (result/flat-map-ok (fn [r] (when-let [u (r :username)]
                                    {:status 200
                                     :headers {"Content-Type" "application/json"}
                                     :body (json/write-str
                                            (db/list-passwords db/db u))})))
      (result/flat-map-err
       (constantly (errors :auth-error)))))

(defn user-registered
  "Handles user was successfully registered view path"
  [req]
  {:status 200
   :body (view/succeed-registration req)})

(defn register-user
  "Handles register user path"
  [req]
  (-> req parse-post-body
      (result/of #(seq %))
      (result/flat-map-ok parse-multiple-query)
      (result/flat-map-ok (fn [r] (result/of r #(and (:username %) (:password %)))))
      (result/flat-map-ok (fn [{:keys [:username :password]}]
                            (db/insert-user db/db username password)
                            (user-registered username)))
      (result/flat-map-err (fn [e]
                             {:status 400
                              :body (str "can't register the user because of: "
                                         (e :info))}))))

(defn register-view
  "Handles register user view path"
  [req]
  {:status 200
   :body (view/register req)})

(defn add-entry
  "Handles new password entry path"
  [req]
  (-> req
      (result/flat-map-ok
       (fn [r] (-> r parse-post-body parse-multiple-query
                   (result/flat-map-ok
                    (fn [q] (result/of q
                                       #(and (:url %) (:login %)
                                             (:urlpassword %)))))
                   (result/flat-map-ok
                    #(let [{:keys [url login urlpassword]} %
                           u (:username r)]
                       (db/insert-pass! db/db u url login urlpassword)
                       {:status 200 :body "new entry succefully added"})))))
      (result/flat-map-err
       (constantly (errors :auth-error)))))

(def random-password
  "Handles random password generation"
  (comp
   (partial assoc {:status 200} :body)
   view/random-password
   (fnil gen/generate-random-symbols 6)
   (fnil parse-long "6") second
   (fnil parse-query "length=6") :query-string))

(defn update-password
  "Handles update password path"
  [req]
  (-> req
      (result/flat-map-ok
       (fn [r]
         (-> r :query-string parse-multiple-query
             (result/flat-map-ok
              (fn [q] (result/of q #(and (:id %) (:newpassword %)
                                         (seq (:id %)) (seq (:newpassword %))))))
             (result/flat-map-ok
              #(when-let [{:keys [id newpassword]} %]
                 (db/update-password db/db newpassword (parse-long id))
                 {:status 200
                  :body (format "password with id %s for user %s was updated" id
                                (:username r))})))))
      (result/flat-map-err
       (constantly (errors :auth-error)))))



(defn update-url
  "Handles update url path"
  [req]
  (-> req
      (result/flat-map-ok
       (fn [r]
         (-> r :query-string parse-multiple-query
             (result/flat-map-ok
              (fn [q] (result/of q #(and (:id %) (:newurl %)
                                         (seq (:id %)) (seq (:newurl %))))))
             (result/flat-map-ok
              #(when-let [{:keys [id newurl]} %]
                 (db/update-url db/db newurl (parse-long id))
                 {:status 200
                  :body (format "url with id %s for user %s was updated" id
                                (:username req))})))))
      (result/flat-map-err
       (constantly (errors :auth-error)))))

(defn update-login
  "Handles update login path"
  [req]
  (-> req
      (result/flat-map-ok
       (fn [r]
         (-> r :query-string parse-multiple-query
             (result/flat-map-ok
              (fn [q] (result/of q #(and (:id %) (:newlogin %)
                                         (seq (:id %)) (seq (:newlogin %))))))
             (result/flat-map-ok
              #(when-let [{:keys [id newlogin]} %]
                 (db/update-login db/db newlogin (parse-long id))
                 {:status 200
                  :body (format "login with id %s for user %s was updated" id
                                (:username req))})))))

      (result/flat-map-err
       (constantly (errors :auth-error)))))

(defn remove-password
  "Handles password removing path"
  [req]
  (-> req
      (result/flat-map-ok
       (fn [r]
         (-> r :query-string parse-multiple-query
             (result/flat-map-ok
              (fn [q] (result/of q #(and (:id %) (seq (:id %))))))
             (result/flat-map-ok
              #(when-let [{:keys [id]} %]
                 (db/remove-entry db/db (parse-long id))
                 {:status 200
                  :body (format "password with id %s for user %s was removed"
                                id (:username req))})))))
      (result/flat-map-err
       (constantly (errors :auth-error)))))

(defn login
  "Handles login view path"
  [req]
  {:status 200
   :body (view/login req)})

(defn logout
  "Handles logging out of the user"
  [req]
  (let [cookie-token (parse-cookie (get-in req [:headers "cookie"]) "token")]
    (->
     cookie-token
     (result/of #(not (nil? %)))
     (result/flat-map-ok #(do
                            (system/remove-session-token! system/system %)
                            {:status 200
                             :headers {"Set-Cookie" "token=deleted; path=/; expires=Thu, 01 Jan 1970 00:00:00 GMT"}
                             :body (str "User was successfully logged out")}))
     (result/flat-map-err (constantly {:err {:info "no user to log-out"}})))))

(defn login-post [req]
  (-> req
      (result/flat-map-ok (fn [r]
                            {:status 200
                             :headers {"Access-Control-Allow-Origin" "*"}
                             :body (format "User %s is successfully authorized"
                                           (:username r))}))
      (result/flat-map-err
       (constantly (errors :auth-error)))))

(def handler
  "Handlers map"
  {:login login
   :root-view root-view
   :add {:response add-entry
         :middleware [#(-> % already-logged?
                           (result/flat-map-err
                            (comp auth-token :payload))
                           (result/flat-map-err
                            (comp auth-up :payload)))]}
   :update-password {:response update-password
                     :middleware [auth?]}
   :update-url {:response update-url
                :middleware [auth?]}
   :update-login {:response update-login
                  :middleware [auth?]}
   :remove-password {:response remove-password
                     :middleware [auth?]}
   :list-passwords {:response list-passwords
                    :middleware [auth?]}
   :list-view {:response
               #(-> % list-view (save-token-to-cookie (:ok %)))
               :middleware [#(-> % already-logged? (result/flat-map-err (comp auth-token :payload)) (result/flat-map-err (comp auth-up :payload)))]}
   :register-view register-view
   :register {:response #(-> %
                             (result/flat-map-ok  (fn [_]  {:status 200 :body (view/logout?)}))
                             (result/flat-map-err (fn [r]  (register-user (:payload r)))))
              :middleware [already-logged?]}
   :user-registered user-registered
   :random-password random-password
   :logout logout
   :login-post {:response #(-> % login-post (save-token-to-cookie (:ok %)))
                :middleware [#(-> % already-logged?
                                  (result/flat-map-err
                                   (comp auth-token :payload))
                                  (result/flat-map-err
                                   (comp auth-body :payload)))]}
   :create-db! (fn [_] (db/create-db! db/db))})

(defn dispatch
  "Dispatches routes, matching type over Handler map and calls 
   matched function with payload as argument, if there is 
   middleware presented then calls all middlewares functions
   on the payload data finally calls response function over
   all middleware functions 
   "
  [{:keys [type payload]}]
  (let [f (get handler type)]
    (if (:middleware f)
      (let [fx (:response f)
            m (:middleware f)
            mws (reduce (fn [acc v] (v acc)) payload m)]
        (fx mws))
      (f payload))))

