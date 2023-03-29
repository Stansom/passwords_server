(ns passman.result-test
  (:require [passman.result :as res]
            [clojure.test :refer [testing is deftest]]))

(deftest result-test
  (testing "Is OK"
    (let [r (res/ok 10)]
      (is (= true (res/ok? r)))))

  (testing "Is ERR"
    (let [r (res/err 22)]
      (is (= true (res/err? r)))))

  (testing "Maps OK if result is OK properly"
    (let [o (res/ok 41)
          m (res/map-ok o inc)]
      (is (= 42 (:ok m)))))

  (testing "Maps OK when result is ERR properly"
    (let [o (res/err 12)
          m (res/map-ok o inc)]
      (is (= true (res/err? m)))))

  (testing "Flat-Map OK if result is OK properly"
    (let [o (res/ok 41)
          m (res/flat-map-ok o inc)]
      (is (= 42 m))))

  (testing "Flat-Map OK when result is ERR properly"
    (let [o (res/err 12)
          m (res/flat-map-ok o inc)]
      (is (= true (res/err? m)))))

  (testing "Maps ERR if result is ERR properly"
    (let [o (res/err 12)
          m (res/map-err o inc)]
      (is (= 13 (:err m)))))

  (testing "Maps ERR if result is OK properly"
    (let [o (res/ok 43)
          m (res/map-err o dec)]
      (is (= true (res/ok? m)))))

  (testing "Flat-Map ERR if result is ERR properly"
    (let [o (res/err 13)
          m (res/flat-map-err o dec)]
      (is (= 12 m))))

  (testing "Flat-Map ERR when result is OK properly"
    (let [o (res/ok 12)
          m (res/flat-map-err o inc)]
      (is (= true (res/ok? m))))))