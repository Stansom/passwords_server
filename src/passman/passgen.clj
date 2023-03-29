(ns passman.passgen
  (:require
   [clojure.string :as s]))

(def symbols "abcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789!@#$%^&*")

(defn exclude-spec-characters [s]
  (s/replace s #"(?i)[^0-9a-z]" ""))

(defn generate-random-symbols
  ([length]
   (let [sh (fn [_] (shuffle (seq symbols)))
         c (shuffle (mapcat sh (range 5)))]
     (apply str (take length c)))))
