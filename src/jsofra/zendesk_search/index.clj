(ns jsofra.zendesk-search.index
  "Functions for creating search indexes for the Zendesk data."
  (:require [clojure.data.json :as json]
            [clojure.java.io :as io]))

(def users (with-open [reader (io/reader (io/resource "data/users.json"))]
             (json/read reader)))
