(ns jsofra.zendesk-search.cli-test
  (:require [clojure.test :refer :all]
            [jsofra.zendesk-search.cli :as cli]
            [jsofra.zendesk-search.catalogues :as catalogues]
            [jsofra.zendesk-search.search :as search]))

;; make our own println so that the real one may be redefed
(def println* (fn [& more]
                (binding [*print-readably* nil]
                  (with-out-str (apply prn more)))))

(defn test-scenario
  "
  Simulate a test scenario by running a end-to-end test supplying all the user input and expected message output.
  Exercises real calls to the db.

  `println` calls are mocked out and the output captured for comparison with the `expected-messages`.
  `real-line` calls are mocked out and the input provided in order by the `responses`.
  "
  [db responses params expected-messages]
  (let [print-statements (atom [])
        responses        (atom responses)]
    (with-redefs [cli/quit  (fn [] (println cli/quit-message))
                  println   (fn [& more]
                              (swap! print-statements conj (apply println* more)))
                  read-line (fn [] (prn (first @responses)) (ffirst (swap-vals! responses rest)))]
      (cli/-main)
      (doseq [[message-fn print-statement] (map vector expected-messages @print-statements)]
        (is (clojure.string/starts-with? print-statement (message-fn db params)))))))

(deftest ^:integration main-test

  (testing "End-to-end scenarios tests"

    (let [db (-> "./catalogues.edn"
                 catalogues/read-config
                 catalogues/read-catalogues
                 search/build-inverted-indexes)]

      (testing "Full successful search run."
        (test-scenario db
                       ["" "s" "users" "_id" "20" "" "q"]
                       {:catalogue "users" :field "_id" :value "20"}
                       [(fn [& _] cli/welcome-message)
                        cli/pause-message
                        cli/options-message
                        cli/search-catalogue-message
                        cli/search-field-message
                        cli/search-value-message
                        ;; fipp pprint adds an extra newline
                        (fn [& _] "\n")
                        cli/search-results-message
                        cli/options-message
                        (fn [& _] cli/quit-message)]))

      (testing "Immediate quit run."
        (test-scenario db
                       ["" "q"]
                       {}
                       [(fn [& _] cli/welcome-message)
                        cli/pause-message
                        cli/options-message
                        (fn [& _] cli/quit-message)]))

      (testing "No results found search run."
        (test-scenario db
                       ["" "s" "users" "_id" "-1" "" "q"]
                       {:catalogue "users" :field "_id" :value "-1"}
                       [(fn [& _] cli/welcome-message)
                        cli/pause-message
                        cli/options-message
                        cli/search-catalogue-message
                        cli/search-field-message
                        cli/search-value-message
                        cli/search-results-message
                        cli/options-message
                        (fn [& _] cli/quit-message)]))

      (testing "Unknown catalogue search run."
        (test-scenario db
                       ["" "s" "UNKNOWN" "" "q"]
                       {:catalogue "UNKNOWN"}
                       [(fn [& _] cli/welcome-message)
                        cli/pause-message
                        cli/options-message
                        cli/search-catalogue-message
                        (fn [& _] (cli/search-catalogue-error-message "UNKNOWN"))
                        cli/options-message
                        (fn [& _] cli/quit-message)]))

      (testing "Unknown field search run."
        (test-scenario db
                       ["" "s" "users" "UNKNOWN" "" "q"]
                       {:catalogue "users" :field "UNKNOWN"}
                       [(fn [& _] cli/welcome-message)
                        cli/pause-message
                        cli/options-message
                        cli/search-catalogue-message
                        cli/search-field-message
                        (fn [& _] (cli/search-field-error-message "users" "UNKNOWN"))
                        cli/options-message
                        (fn [& _] cli/quit-message)])))))
