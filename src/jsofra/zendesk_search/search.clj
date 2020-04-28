(ns jsofra.zendesk-search.search
  "Functions for creating search indexes for the Zendesk data."
  (:require [clojure.data.json :as json]
            [clojure.java.io :as io]))

(defn read-entities [path]
  (with-open [reader (io/reader (io/resource path))]
    (json/read reader :key-fn keyword)))

(defn read-data []
  {:users         (read-entities "data/users.json")
   :organizations (read-entities "data/organizations.json")
   :tickets       (read-entities "data/tickets.json")})

(defn invert-entity [index entity]
  (reduce-kv (fn [m k v]
               (assoc m k (into {} (map vector (if (coll? v) v [v]) (repeat [index])))))
             {} entity))

(defn build-inverted-index [entities]
  (apply merge-with
         (partial merge-with into)
         (map-indexed invert-entity entities)))

(defn build-inverted-indexes [entities-map]
  {:entities         entities-map
   :inverted-indexes (reduce-kv (fn [m k entities]
                                  (assoc m k (build-inverted-index entities)))
                                {} entities-map)})

(defn lookup-entities [{:keys [entities inverted-indexes]} [entity-type field value]]
  (let [entity-indexes (get-in inverted-indexes [entity-type field value])]
    (map (get entities entity-type) entity-indexes)))
