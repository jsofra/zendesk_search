(ns jsofra.zendesk-search.search-test
  (:require [clojure.test :refer :all]
            [jsofra.zendesk-search.search :as search]))

(def test-entities [{"string"   "string"
                     "int"      1
                     "bool"     false
                     "vector"   ["one" "two" "three"]
                     "sentence" "One, Two."}
                    {"string" "string"
                     "int"    2
                     "bool"   true
                     "vector" ["one" "three" "four"]}
                    {"string" "string-new"
                     "int"    3
                     "bool"   false
                     "vector" ["two" "four"]}
                    {"int"    4
                     "bool"   false
                     "vector" ["five"]}])

(deftest normalize-value-test

  (are [normalized-value value] (= normalized-value (search/normalize-value value))
    "hello" "hElLo"
    "100"   100
    "true"  true))

(deftest analyze-values-test

  (testing "Analyzing flattens and normalizes values."
    (are [analized-values values] (= analized-values (search/analyze-values nil values))
      ["hello"]                     "hello"
      ["one" "two" "three"]         ["one" "two" "three"]
      ["one" "2" "true"]            ["one" 2 true]
      ["one" "1" "two" "2" "3" "4"] {"one" 1 "two" [2 3 4]}))

  (testing "Analyzing applies regex analyzer to all values."
    (let [sentence-1          "Hello, my name is Nikki."
          sentence-expected-1 ["hello, my name is nikki." "hello" "my" "name" "is" "nikki"]
          sentence-2          "Another sentence, 'Hi there!'."
          sentence-expected-2 ["another sentence, 'hi there!'." "another" "sentence" "hi" "there"]]
      (are [analized-values values] (= analized-values (search/analyze-values "\\w+" values))
        sentence-expected-1                              sentence-1
        sentence-expected-2                              sentence-2
        (concat sentence-expected-1 sentence-expected-2) [sentence-1 sentence-2]))))

(deftest invert-entity-test

  (testing "Inversion of an entity"
    (testing "Should now be a path field->value->[index] for each field."
      (is (= {"string"   {"string" [0]}
              "int"      {"1" [0]}
              "bool"     {"false" [0]}
              "vector"   {"one" [0] "two" [0] "three" [0]}
              "sentence" {"one, two." [0]}}
             (search/invert-entity {} 0 (first test-entities)))))

    (testing "Should apply analyzing."
      (is (= {"one, two." [0], "one" [0], "two" [0]}
             (get (search/invert-entity {"sentence" "\\w+"} 0 (first test-entities)) "sentence"))))))

(deftest build-inverted-index-test

  (testing "The building of a full inverted index of a catalogue."
    (testing "Inverting a single entity should be equivalent to inverting a sequence with a single entity."
      (is (= (search/invert-entity {} 0 (first test-entities))
             (search/build-inverted-index {:entities (take 1 test-entities) :analyzers {}}))))

    (testing "Inverting multiple entities should merge the values and have multiple indexes per value."
      (is (= {"string"   {"string"     [0 1]
                          "string-new" [2]}
              "int"      {"1" [0]
                          "2" [1]
                          "3" [2]
                          "4" [3]}
              "bool"     {"false" [0 2 3]
                          "true"  [1]}
              "vector"   {"one"   [0 1]
                          "two"   [0 2]
                          "three" [0 1]
                          "four"  [1 2]
                          "five"  [3]}
              "sentence" {"one, two." [0]}}
             (search/build-inverted-index {:entities test-entities :analyzers {}}))))))

(deftest build-inverted-indexes-test

  (testing "The building of inverted-indexes (database)."
    (let [catalogue          {:entities test-entities :analyzers {}}
          catalogues         {:catalogue-1 catalogue
                              :catalogue-2 catalogue}
          inverted-catalogue (search/build-inverted-index catalogue)]
      (is (= {:catalogues       catalogues
              :inverted-indexes {:catalogue-1 inverted-catalogue
                                 :catalogue-2 inverted-catalogue}}
             (search/build-inverted-indexes catalogues))))))

(deftest lookup-entites-test

  (let [database (search/build-inverted-indexes {:test {:entities test-entities :analyzers {}}})]
    (testing "Looking up entities with simple value"
      (is (= (subvec test-entities 0 2)
             (search/lookup-entities database [:test "string" "string"]))))

    (testing "Looking up entities with a value in a vector"
      (is (= (subvec test-entities 1 3)
             (search/lookup-entities database [:test "vector" "four"]))))

    (testing "Looking up entities with empty values"
      (is (= (subvec test-entities 3 4)
             (search/lookup-entities database [:test "string" nil]))))))

(deftest select-fields-test

  (is (= [{"id" 1 "bool" false}
          {"id" 2 "bool" true}
          {"id" 3 "bool" false}
          {"id" 4 "bool" false}]
         (search/select-fields {"int" "id" "bool" "bool"} test-entities)))

  (is (= test-entities
         (search/select-fields nil test-entities))))
