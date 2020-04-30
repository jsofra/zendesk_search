(ns jsofra.zendesk-search.search-test
  (:require [clojure.test :refer :all]
            [jsofra.zendesk-search.search :as search]))

(def test-entities [{:string "string"
                     :int    1
                     :bool   false
                     :vector ["one" "two" "three"]}
                    {:string "string"
                     :int    2
                     :bool   true
                     :vector ["one" "three" "four"]}
                    {:string "string-new"
                     :int    3
                     :bool   false
                     :vector ["two" "four"]}
                    {:int    4
                     :bool   false
                     :vector ["five"]}])

(deftest invert-entity-test

  (is (= {:string {"string" [0]}
          :int    {1 [0]}
          :bool   {false [0]}
          :vector {"one" [0] "two" [0] "three" [0]}}
         (search/invert-entity 0 (first test-entities)))))

(deftest build-inverted-index-test

  (is (= {:string {"string" [0]}
          :int    {1 [0]}
          :bool   {false [0]}
          :vector {"one" [0] "two" [0] "three" [0]}}
         (search/build-inverted-index (take 1 test-entities))))

  (is (= {:string {"string"     [0 1]
                   "string-new" [2]}
          :int    {1 [0]
                   2 [1]
                   3 [2]
                   4 [3]}
          :bool   {false [0 2 3]
                   true  [1]}
          :vector {"one"   [0 1]
                   "two"   [0 2]
                   "three" [0 1]
                   "four"  [1 2]
                   "five" [3]}}
         (search/build-inverted-index test-entities))))

(deftest lookup-entites-test

  (testing "Looking up entities with simple value"
    (is (= (subvec test-entities 0 2)
           (search/lookup-entities (search/build-inverted-indexes {:test test-entities}) [:test :string "string"]))))

  (testing "Looking up entities with a value in a vector"
    (is (= (subvec test-entities 1 3)
           (search/lookup-entities (search/build-inverted-indexes {:test test-entities}) [:test :vector "four"]))))

  (testing "Looking up entities with empty values"
    (is (= (subvec test-entities 3 4)
           (search/lookup-entities (search/build-inverted-indexes {:test test-entities}) [:test :string nil])))))

(deftest select-fields-test

  (is (= [{:id 1 :bool false}
          {:id 2 :bool true}
          {:id 3 :bool false}
          {:id 4 :bool false}]
         (search/select-fields {:int :id :bool :bool} test-entities)))

  (is (= test-entities
         (search/select-fields nil test-entities))))
