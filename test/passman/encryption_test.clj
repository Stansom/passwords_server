(ns passman.encryption-test
  (:require [passman.encryption :as en]
            [clojure.test :refer [testing is deftest]]
            [clojure.core :as c]))

(deftest encryption-test
  (testing "Encryption is working"
    (let [pass "plumbinG123#@&*#^%"
          enc (en/encrypt-pass pass)
          decr (en/decrypt-pass enc)]
      (is (= pass decr))))

  (testing "Encryption is not working with empty pass"
    (let [pass ""
          enc (en/encrypt-pass pass)]
      (is (= {:err {:info "password can't be empty"}} enc))))

  (testing "Decryption fails with NIL if wrong encryption"
    (let [pass ""
          enc (en/encrypt-pass pass)
          decr (en/decrypt-pass enc)]
      (is (nil? decr)))))
