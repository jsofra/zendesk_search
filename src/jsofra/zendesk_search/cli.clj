(ns jsofra.zendesk-search.cli
  (:require [jsofra.zendesk-search.catalogues :as catalogues]
            [jsofra.zendesk-search.search :as search]
            [clojure.tools.cli :as cli])
  (:gen-class))

(def catalogues {:users         "./catalogues/users.json"
                 :organizations "./catalogues/organizations.json"
                 :tickets       "./catalogues/tickets.json"})

(defn -main
  "I don't do a whole lot ... yet."
  [& args]
  (catalogues/read-catalogues catalogues)

  (println "Hello, World!"))
