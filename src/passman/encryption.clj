(ns passman.encryption
  (:require [babashka.pods :as pods]
            [clojure.string :as str]))
(pods/load-pod 'org.babashka/buddy "0.3.3")
(require '[pod.babashka.buddy.core.codecs :as codecs]
         '[pod.babashka.buddy.core.hash :as hash]
         '[pod.babashka.buddy.core.crypto :as crypto])

(def encr-iv "bonyfanboydonopo")
(def encr-key (hash/sha256 "hyperhyper"))

(defn encrypt-pass [p]
  (if (seq p)
    (-> (crypto/block-cipher-encrypt (codecs/to-bytes p) encr-key encr-iv
                                     {:alg :aes128-cbc-hmac-sha256})
        (codecs/bytes->hex))
    {:err {:info "password can't be empty"}}))

(defn decrypt-pass [en]
  (when-not (empty? en)
    (try
      (-> (crypto/block-cipher-decrypt (codecs/hex->bytes en) encr-key encr-iv {:alg :aes128-cbc-hmac-sha256})
          (codecs/bytes->str))
      (catch Exception _ nil))))

(defn encr-token [u p]
  (encrypt-pass (str u ":;" p)))

(defn decrypt-token [t]
  (let [de (decrypt-pass t)]
    (when de
      (str/split de #":;"))))


