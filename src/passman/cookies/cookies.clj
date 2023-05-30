(ns passman.cookies.cookies
  (:require [clojure.string :as str]
            [passman.utils.dates :refer [date-to-utc hours-to-date]]
            [passman.utils.queries-parser :refer [parse-query]]))


(defn gen-cookie
  "Generates cookie string with key (k) and value (v), can take
   expire date as third argument (exp)"
  ([k v]
   (gen-cookie k v nil))
  ([k v exp]
   (str k "=" v "; HttpOnly; SameSite=Lax; Secure" (when exp (str "; Expires=" exp "; ")) "path=" "/" ";")))

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