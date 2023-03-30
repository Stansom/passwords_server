(ns passman.passgen
  #_(:require [clojure.string :as s]))

(def symbols "abcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789!@#$%^&*")

#_(defn exclude-spec-characters [s]
    (s/replace s #"(?i)[^0-9a-z]" ""))

(defn generate-random-symbols
  ([length]
   (let [sh (fn [_] (shuffle (seq symbols)))
         c (shuffle (mapcat sh (range 5)))]
     (apply str (take length c)))))
