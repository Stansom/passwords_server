(ns passman.auth.auth
  (:require [passman.cookies.cookies :refer [parse-cookie parse-req-cookie]]
            [passman.db :as db]
            [passman.encryption :as encryption]
            [passman.result :as result]
            [passman.system :as system]
            [passman.utils.queries-parser :refer [parse-multiple-query
                                                  parse-post-body]]))

(defn- auth-user-db
  "Checks that such user exist in DB"
  [user pass]
  (-> (db/select-from db/db :users [:*]
                      [:and
                       [:= :username user]
                       [:= :password (encryption/encrypt-pass pass)]])
      first (result/of (complement nil?))))


(defn- wrap-username
  "Conjoin username to REQ map"
  [req user]
  (assoc req :username user))

(defn- wrap-password
  "Conjoin password to REQ map"
  [req pass]
  (assoc req :password pass))

(defn- wrap-authorized
  "Adds authorized field to REQ"
  [req]
  (assoc req :authorized true))

(defn- persist-token-to-headers
  "Conjoin token to REQ header"
  [req t]
  (assoc-in req [:headers "token"] t))

(defn- persist-and-wrap-token
  "Encrypt token then save it to the Systems session tokens list
   and adds username field to REQ map"
  [req]
  (fn [{:keys [users/username users/password]}]
    (let [decr-pass (encryption/decrypt-pass password)
          encr-t (encryption/encr-token username decr-pass)
          _ (system/persist-session-token! system/system encr-t)]
      (-> (wrap-username req username)
          (wrap-password password)
          (persist-token-to-headers encr-t)
          (wrap-authorized)))))

(defn- auth-err-handler [req]
  {:payload (dissoc req :authorized)
   :info :not-authorized})

(defn auth-token
  "Attempts token-based authorization located in the headers."
  [req]
  (-> req :headers (get "token")
      (result/of #(seq %))
      (result/flat-map-ok #(-> (encryption/decrypt-token %) (result/of (complement nil?))))
      (result/flat-map-ok (fn [r] (-> r (result/of #(and (seq (first %)) (seq (second %)))))))
      (result/flat-map-ok
       #(auth-user-db (first %) (second %)))
      (result/map-ok (persist-and-wrap-token req))
      (result/map-err (constantly (auth-err-handler req)))))

(defn auth-up
  "Tries to authorize by user/password presented in the query-string"
  [req]
  (-> req :query-string (parse-multiple-query)
      (result/flat-map-ok (fn [r] (-> r (result/of #(and (:username %) (:password %))))))
      (result/flat-map-ok
       #(auth-user-db (:username %) (:password %)))
      (result/map-ok (persist-and-wrap-token req))
      (result/map-err (constantly (auth-err-handler req)))))

(defn auth-cookie
  "Tries to authorize by cookies user/password fields"
  [req]
  (-> req
      (result/of #(seq (get-in % [:headers "cookie"])))
      (result/flat-map-ok parse-req-cookie)
      (result/of #(and (:username %) (:password %)))
      (result/flat-map-ok
       #(auth-user-db (:username %) (:password %)))
      (result/map-ok (persist-and-wrap-token req))
      (result/map-err (constantly (auth-err-handler req)))))

(defn auth-body
  "Tries to authorize REQ bodys user/password fields"
  [req]
  (-> req parse-post-body
      (result/of #(seq %))
      (result/flat-map-ok parse-multiple-query)
      (result/flat-map-ok (fn [r] (-> r (result/of #(and (:username %) (:password %))))))
      (result/flat-map-ok
       #(auth-user-db (:username %) (:password %)))
      (result/map-ok (persist-and-wrap-token req))
      (result/map-err (constantly (auth-err-handler req)))))

(defn already-logged?
  "Checks if user already logged-in via cookies and session token comparing"
  [req]
  (-> req :headers (get "cookie") (parse-cookie "token")
      (result/of #(seq %))
      (result/flat-map-ok #(-> (system/check-session-token system/system %)
                               (result/of (complement nil?))))
      (result/map-ok #(-> (persist-token-to-headers req %)
                          (wrap-username (first
                                          (encryption/decrypt-token %)))))
      (result/map-err (constantly (auth-err-handler req)))))

(defn auth?
  "Passes REQ through authorization functions, returning authorized REQ if successful, 
   or an error map otherwise."
  [req]
  (-> req auth-token
      (result/flat-map-err (comp auth-up :payload))
      (result/flat-map-err (comp auth-body :payload))
      (result/flat-map-err (comp auth-cookie :payload))
      (result/map-ok identity)
      (result/map-err (constantly (auth-err-handler req)))))
