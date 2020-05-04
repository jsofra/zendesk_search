(ns jsofra.zendesk-search.catalogues-test
  (:require [clojure.test :refer :all]
            [jsofra.zendesk-search.catalogues :as catalogues]))

(deftest ^:unit build-query-test

  (testing "Test replacement of field and value when building a query from configuration."
    (let [test-catalogues {:catalogue-1 {:query {:find [:catalogue-1 :field? :value?]
                                                 :as   :catalogue-1}}
                           :catalogue-2 {:query {:find [:catalogue-2 :field? :value?]
                                                 :as   :catalogue-2}}}]
      (is (= {:find [:catalogue-1 "test-field" "test-value"]
              :as   :catalogue-1}
             (catalogues/build-query test-catalogues {:catalogue :catalogue-1
                                                      :field     "test-field"
                                                      :value     "test-value"}))))))
