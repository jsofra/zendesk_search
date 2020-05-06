(ns jsofra.zendesk-search.integration.search-test
  (:require [clojure.test :refer :all]
            [jsofra.zendesk-search.catalogues :as catalogues]
            [jsofra.zendesk-search.search :as search]))

;; Tests the integration of the catalogue config reading
;; the query building and the search functionality
(deftest ^:integration query-test

  (testing "Tests some queries against the database from configuration."
    (let [db (-> ".zendesk-search/catalogues.edn"
                 catalogues/read-config
                 catalogues/read-catalogues
                 search/build-inverted-indexes)

          user-id           20
          organization-name "Xylar"
          ticket-id         "7523607d-d45c-4e3a-93aa-419402e64d73"]

      (testing "Users query and their relationships."
        (let [query  (catalogues/build-query (:catalogues db)
                                             {:catalogue :users
                                              :field     "_id"
                                              :value     (str user-id)})
              result (search/query db query)]
          (is (= user-id (get-in result [:users 0 "_id"])))
          (is (= organization-name (get-in result [:users 0 "organization_name"])))
          (is (= [{"ticket_id" ticket-id
                   "subject"   "A Catastrophe in Sao Tome and Principe"}
                  {"ticket_id" "e34262a7-df37-4715-a482-fb0acb5d0b46"
                   "subject"   "A Drama in Mongolia"}
                  {"ticket_id" "189eed9f-b44c-49f3-a904-2c482193996a"
                   "subject"   "A Catastrophe in Singapore"}]
                 (get-in result [:users 0 "submitted_tickets"])))))

      (testing "Organizations query and their relationships."
        (let [query  (catalogues/build-query (:catalogues db)
                                             {:catalogue :organizations
                                              :field     "name"
                                              :value     organization-name})
              result (search/query db query)]
          (is (= organization-name (get-in result [:organizations 0 "name"])))
          (is (= [{"name" "Ingrid Wagner"}
                  {"name" "Lou Schmidt"}
                  {"name" "Lee Davidson"}
                  {"name" "Jessica Raymond"}]
                 (get-in result [:organizations 0 "users"])))))

      (testing "Tickets query and their relationships."
        (let [query  (catalogues/build-query (:catalogues db)
                                             {:catalogue :tickets
                                              :field     "_id"
                                              :value     ticket-id})
              result (search/query db query)]
          (is (= ticket-id (get-in result [:tickets 0 "_id"])))
          (is (= user-id (get-in result [:tickets 0 "submitter_id"]))))))))
