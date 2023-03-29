(ns passman.passgen-test
  (:require [passman.passgen :as pg]
            [clojure.test :refer [testing is deftest]]))

(deftest password-generation-test
  (testing "Generates right amount of symbols"
    (is (= 20 (count (pg/generate-random-symbols 20))))))