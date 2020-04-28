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
                     :vector ["two" "four"]}])

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
                   3 [2]}
          :bool   {false [0 2]
                   true  [1]}
          :vector {"one"   [0 1]
                   "two"   [0 2]
                   "three" [0 1]
                   "four"  [1 2]}}
         (search/build-inverted-index test-entities))))

(deftest lookup-entites-test

  (is (= (take 2 test-entities)
         (search/lookup-entities (search/build-inverted-indexes {:test test-entities}) [:test :string "string"])))

  (is (= (rest test-entities)
         (search/lookup-entities (search/build-inverted-indexes {:test test-entities}) [:test :vector "four"]))))
