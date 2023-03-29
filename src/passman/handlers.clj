(ns passman.handlers
  (:require
   [babashka.json :as json]
   [clojure.string :as str]
   [passman.db :as db]
   [passman.encryption :as encryption]
   [passman.result :as result]
   [passman.view :as view]
   [passman.passgen :as gen]
   [passman.system :as system]
   [clojure.core :as c]))

(defn parse-query
  "Parse uname=name to [uname name]"
  [q]
  (let [[qry param] (str/split q #"=")
        param (when param (first (str/split param #";")))]
    [qry param]))

(defn parse-multiple-query
  "Parse uname=name&upass=pass1 to {:uname \"name\" :upass \"pass1\"}"
  [q]
  (if-not (empty? q)
    (let [s (str/split q #"&")]
      {:ok (reduce (fn [acc st]
                     (let [[k v] (parse-query st)]
                       (assoc acc (keyword k) v))) {} s)})
    {:err {:info "can't parse query"}}))

(defn check-pers
  "Checks that keys (ks) are presented and if all good
   calls function (f)"
  [ks f]
  (if (and
       (seq ks)
       (every? seq ks))
    (f)
    {:err {:info "all keys must be presented"}}))

(defn parse-post-body
  "Parse REQ body from input stream to string"
  [req]
  (when-not (nil? (:body req))
    (when-let [b (slurp (:body req))]
      b)))

(defn date-to-utc
  "Converts INST to UTC timezone"
  [inst]
  (-> (java.time.format.DateTimeFormatter/ofPattern "E, dd MMM yyyy HH:mm:ss z")
      (.withZone java.time.ZoneOffset/UTC)
      (.format inst)))

(defn hours-to-date
  "Adds hours (h) to present day"
  [h]
  (.plusMillis (java.time.Instant/now) (* 3600000 h)))

(defn gen-cookie
  "Generates cookie string with key (k) and value (v), can take
   expire date as third argument (exp)"
  ([k v]
   (gen-cookie k v nil))
  ([k v exp]
   (str k "=" v (when exp (str "; Expires=" exp "; ")) "path=" "/" ";")))

(defn parse-req-cookie
  "Returns REQ cookies as a map
   \"cookie-1=val-1; cookie-2=val-2;\"
   {:cookie-1 val-1 :cookie-2 val-2}"
  [req]
  (let [spl (map str/trim (str/split (get-in req [:headers "cookie"]) #";"))
        parsed (into {} (map (comp (juxt (comp keyword str/lower-case first) second) parse-query) spl))]
    parsed))

(defn parse-cookie
  "Takes exact cookie (item) from cookie string"
  [c item]
  (when (seq c)
    (let [parsed (second (str/split c (re-pattern (str item "="))))]
      (when parsed
        (str/replace parsed ";" "")))))

(defn save-token-to-cookie
  "Takes token from REQ headers field and if there's no token in cookies
   sets the token to RESP map headers field"
  ([res req]
   ((save-token-to-cookie req) res))
  ([req]
   (fn [res]
     (let [t (get-in req [:headers "token"])
           cookie-token (parse-cookie (get-in req [:headers "cookie"]) "token")]
       (if (and (seq t) (not (seq cookie-token)))
         (update-in res [:headers "Set-Cookie"] conj (gen-cookie "token" t
                                                                 (date-to-utc (hours-to-date 24)))
                    #_{"Set-Cookie" (gen-cookie "token" t
                                                (date-to-utc (hours-to-date 24)))})
         res)))))

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

(defn- auth-user-db
  "Checks that such user exist in DB"
  [user pass]
  (-> (db/select-from db/db :users
                      [:and
                       [:= :username user]
                       [:= :password (encryption/encrypt-pass pass)]])
      first (result/of (complement nil?))))

(defn- auth-err-handler [req]
  {:payload (dissoc req :authorized)
   :info :not-authorized})

(defn auth-token
  "Tries to authorize by token wich located in headers"
  [req]
  (-> req :headers (get "token")
      (result/of #(seq %))
      (result/flat-map-ok #(-> (encryption/decrypt-token %) (result/of (complement nil?))))
      (result/flat-map-ok
       #(auth-user-db (first %) (second %)))
      (result/map-ok (persist-and-wrap-token req))
      (result/map-err (constantly (auth-err-handler req)))))

(defn auth-up
  "Tries to authorize by user/password presented in the query-string"
  [req]
  (-> req :query-string (parse-multiple-query)
      (result/flat-map-ok
       #(auth-user-db (:username %) (:password %)))
      (result/map-ok (persist-and-wrap-token req))
      (result/map-err (constantly (auth-err-handler req)))))

(defn auth-cookie
  "Tries to authorize by cookies user/password fields"
  [req]
  (-> req
      (result/of #(not (nil? (get-in % [:headers "cookie"]))))
      (result/map-ok parse-req-cookie)
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
      (result/map-ok #((persist-and-wrap-token req) %))
      #_(result/map-ok #(wrap-username req (first
                                            (encryption/decrypt-token %))))
      (result/map-err (constantly (auth-err-handler req)))))

(defn auth?
  "Pass REQ thru all authorization functions, then returns
   authorized REQ if the tries was succefful otherwise error map"
  [req]
  (-> req
      (already-logged?)
      (result/flat-map-err (comp auth-token :payload))
      (result/flat-map-err (comp auth-up :payload))
      (result/flat-map-err (comp auth-body :payload))
      (result/flat-map-err (comp auth-cookie :payload))
      (result/map-ok identity)
      (result/map-err (constantly (auth-err-handler req)))))

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
         (let [u (r :username)]
           {:status 200
            :body (view/list-passwords u
                                       (db/list-passwords db/db u))})))
      (result/flat-map-err (fn [e] {:status 401 :body (str (:info e))}))))

(defn list-passwords
  "Handles list passwords path, returns json"
  [req]
  (-> req
      (result/flat-map-ok (fn [r] (let [u (r :username)]
                                    {:status 200
                                     :headers {"Content-Type" "application/json"}
                                     :body (json/write-str
                                            (db/list-passwords db/db u))})))
      (result/flat-map-err (constantly {:status 401 :body :not-authorized}))))

(defn user-registered
  "Handles user was successfully registered view path"
  [req]
  {:status 200
   :body (view/succeed-registration req)})

(defn register-user
  "Handles register user path"
  [req]
  (-> req :query-string parse-multiple-query
      (result/flat-map-ok (fn [{:keys [username password]}]
                            (check-pers [username password]
                                        (fn []
                                          (db/insert-user db/db username password)
                                          {:status 200
                                           :body (str "User " username " was successfully added.")}))))
      (result/flat-map-err (fn [r]
                             {:status 400
                              :body (str (:info r))}))))

(defn register-view
  "Handles register user view path"
  [req]
  (if (= (:status (register-user req)) 200)
    (let [n (get-in (parse-multiple-query (:query-string req)) [:ok :username])] (user-registered n))
    {:status 200
     :body (view/register req)}))

(defn add-entry
  "Handles new password entry path"
  [req]
  (-> req
      (result/flat-map-ok
       (fn [r]
         (result/flat-map-ok
          ((comp parse-multiple-query :query-string) r)
          (fn [{:keys [url login urlpassword]}]
            (check-pers [url login urlpassword]
                        (fn []
                          (let [username (get r :username)]
                            (db/insert-pass! db/db username url login urlpassword)
                            (list-view r))))))))
      (result/flat-map-err (fn [e] {:status 401 :body (str (:info e))}))))

(def random-password
  "Handles random password generation"
  (comp
   (partial assoc {:status 200} :body)
   view/random-password
   (fnil gen/generate-random-symbols 6)
   (fnil parse-long "6") second (fnil parse-query "length=6") :query-string))

(defn update-password
  "Handles update password path"
  [req]
  (-> req
      (result/flat-map-ok (fn [r]
                            (when-let [{{:keys [id username newpassword]} :ok}
                                       (parse-multiple-query (:query-string r))]
                              (db/update-password db/db newpassword (parse-long id))
                              {:status 200
                               :body (format "password with id %s for user %s was updated" id username)})))

      (result/flat-map-err (fn [e] {:status 401 :body (str (:info e))}))))

(defn update-url
  "Handles update url path"
  [req]
  (-> req
      (result/flat-map-ok (fn [r]
                            (when-let [{{:keys [id username newurl]} :ok}
                                       (parse-multiple-query (:query-string r))]
                              (db/update-url db/db newurl (parse-long id))
                              {:status 200
                               :body (format "url with id %s for user %s was updated" id username)})))

      (result/flat-map-err (fn [e] {:status 401 :body (str (:info e))}))))

(defn update-login
  "Handles update login path"
  [req]
  (-> req
      (result/flat-map-ok (fn [r]
                            (when-let [{{:keys [id username newlogin]} :ok}
                                       (parse-multiple-query (:query-string r))]
                              (db/update-login db/db newlogin (parse-long id))
                              {:status 200
                               :body (format "login with id %s for user %s was updated" id username)})))

      (result/flat-map-err (fn [e] {:status 401 :body (str (:info e))}))))

(defn remove-password
  "Handles password removing path"
  [req]
  (-> req
      (result/flat-map-ok (fn [r]
                            (when-let [{{:keys [id username]} :ok}
                                       (parse-multiple-query (:query-string r))]
                              (db/remove-entry db/db (parse-long id))
                              {:status 200
                               :body (format "password with id %s for user %s was removed" id username)})))
      (result/flat-map-err (fn [e] {:status 401 :body (str (:info e))}))))

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
                             :body (str "User was successfully logged out")}))
     (result/flat-map-err (constantly {:err {:info "no user to log-out"}})))))

(def handler
  "Handlers map"
  {:login login
   :root-view root-view
   :add {:response add-entry
         :middleware [auth?]}
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
               :middleware [auth?]}
   :register-view register-view
   :register register-user
   :user-registered user-registered
   :random-password random-password
   :logout logout
   :create-db! (fn [_] (db/create-db! db/db))})

(defn dispatch
  "Dispatches routes, matching type over Handler map and calls 
   matched function with payload as argument, if there is 
   middleware presented then calls all middlewares functions
   on the payload data finally calls response function over
   all middleware functions 
   "
  [{:keys [type payload] :as p}]
  (let [f (get handler type)]
    (if (:middleware f)
      (let [fx (:response f)
            m (:middleware f)
            mws (reduce (fn [acc v] (v acc)) payload m)]
        (fx mws))
      (f payload))))

