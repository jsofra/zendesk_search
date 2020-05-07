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

(deftest ^:unit normalize-value-test

  (are [normalized-value value] (= normalized-value (search/normalize-value value))
    "hello" "hElLo"
    "100"   100
    "true"  true))


(deftest ^:unit analyze-values-test

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


(deftest ^:unit invert-entity-test

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


(deftest ^:unit build-inverted-index-test

  (testing "The building of a full inverted index of a catalogue."
    (testing "Inverting a single entity should be equivalent to inverting a sequence with a single entity."
      (is (= (search/invert-entity {} 0 (first test-entities))
             (search/build-inverted-index {:entities (take 1 test-entities) :analyzers {}}))))

    (testing "Inverting multiple entities should merge the values and have multiple indexes per value."
      (is (= {"string"   {"string"     [0 1]
                          "string-new" [2]
                          ""           [3]}
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
              "sentence" {"one, two." [0]
                          ""          [1 2 3]}}
             (search/build-inverted-index {:entities test-entities :analyzers {}}))))))


(deftest ^:unit build-inverted-indexes-test

  (testing "The building of inverted-indexes (database)."
    (let [catalogue          {:entities test-entities :analyzers {}}
          catalogues         {:catalogue-1 catalogue
                              :catalogue-2 catalogue}
          inverted-catalogue (search/build-inverted-index catalogue)]
      (is (= {:catalogues       catalogues
              :inverted-indexes {:catalogue-1 inverted-catalogue
                                 :catalogue-2 inverted-catalogue}}
             (search/build-inverted-indexes catalogues))))))


(deftest ^:unit lookup-entites-test

  (let [database (search/build-inverted-indexes {:catalogue-1 {:entities test-entities :analyzers {}}})]
    (testing "Looking up entities with simple value"
      (is (= (subvec test-entities 0 2)
             (search/lookup-entities database [:catalogue-1 "string" "string"]))))

    (testing "Looking up entities with a value in a vector"
      (is (= (subvec test-entities 1 3)
             (search/lookup-entities database [:catalogue-1 "vector" "four"]))))

    (testing "Looking up entities with empty values"
      (is (= (subvec test-entities 3 4)
             (search/lookup-entities database [:catalogue-1 "string" nil]))))))


(deftest ^:unit select-fields-test

  (is (= [{"id" 1 "bool" false}
          {"id" 2 "bool" true}
          {"id" 3 "bool" false}
          {"id" 4 "bool" false}]
         (search/select-fields {"int" "id" "bool" "bool"} test-entities)))

  (is (= test-entities
         (search/select-fields nil test-entities))))


(deftest ^:unit database-introspection-fns-test

  (let [database (search/build-inverted-indexes {:catalogue-1 {:entities test-entities :analyzers {}}
                                                 :catalogue-2 {:entities test-entities :analyzers {}}})]
    (is (search/has-catalogue? database :catalogue-1))
    (is (not (search/has-catalogue? database :non-existent-catalogue)))
    (is (search/has-field? database :catalogue-1 "string"))
    (is (not (search/has-field? database :catalogue-1 "non-existent-field")))
    (is (= [:catalogue-1 :catalogue-2] (search/list-catalogues database)))
    (is (= (search/list-fields database :catalogue-1) (search/list-fields database :catalogue-2)))
    (is (= ["bool" "int" "sentence" "string" "vector"] (search/list-fields database :catalogue-1)))))


(deftest ^:unit query-test

  (let [join-test-entities [{"test_id"     1
                             "test_id_2"   4
                             "name"        "test_id_1"
                             "description" "I am a test"}
                            {"test_id"     2
                             "test_id_2"   4
                             "name"        "test_id_2"
                             "description" "I am a test"}
                            {"test_id"     3
                             "test_id_2"   4
                             "name"        "test_id_3"
                             "description" "I am a test"}]

        database (search/build-inverted-indexes {:catalogue-1 {:entities test-entities :analyzers {}}
                                                 :catalogue-2 {:entities join-test-entities :analyzers {}}})]

    (testing "A basic find query."
          (is (= (first (search/lookup-entities database [:catalogue-1 "vector" "four"]))
                 (search/query database {:find [:catalogue-1 "vector" "four"]}))))

    (testing "Aliasing the results of query."
      (is (= {:alias (search/lookup-entities database [:catalogue-1 "vector" "four"])}
             (search/query database {:find [:catalogue-1 "vector" "four"]
                                     :as   :alias}))))

    (testing "A sub-query joining on a 1-to-1 relationship."
      (is (= {"catalogue-1"
              [(merge (nth test-entities 0) {"join_id" 1 "join_name" "test_id_1"})
               (merge (nth test-entities 2) {"join_id" 3 "join_name" "test_id_3"})]}
             (search/query database {:find    [:catalogue-1 "vector" "two"]
                                     :include [{:find   [:catalogue-2 "test_id" "int"]
                                                :select {"test_id" "join_id"
                                                         "name"    "join_name"}}]
                                     :as      "catalogue-1"}))))

    (testing "A sub-query joining on a 1-to-many relationship."
      (is (= (assoc (nth test-entities 3)
                    "joined"
                    [{"id" 1, "description" "I am a test"}
                     {"id" 2, "description" "I am a test"}
                     {"id" 3, "description" "I am a test"}])
             (search/query database {:find    [:catalogue-1 "int" 4]
                                     :include [{:find   [:catalogue-2 "test_id_2" "int"]
                                                :select {"test_id"     "id"
                                                         "description" "description"}
                                                :as     "joined"}]}))))))
