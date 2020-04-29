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

(defn lookup-entities [{:keys [entities inverted-indexes]}
                       [entity-type field value]
                       selection]
  (let [entity-indexes  (get-in inverted-indexes [entity-type field value])
        result-entities (map (get entities entity-type) entity-indexes)]
    (if selection
      (map (fn [entity]
             (-> entity
                 (select-keys (keys selection))
                 (clojure.set/rename-keys selection)))
           result-entities)
      result-entities)))

(defn search
  ([inverted-index-map query]
   (search inverted-index-map query nil))
  ([inverted-index-map {:keys [find find_1 include select]} parent]
   (let [[entity-type field value] (or find find_1)
         value                     (if parent (get parent value) value)
         entities                  (lookup-entities inverted-index-map [entity-type field value] select)]
     (if include
       (for [entity entities]
         (reduce (fn [entity {:keys [find as] :as query}]
                   (let [result (search inverted-index-map query entity)]
                     (merge entity
                            (if find
                              {(or as (first find)) result}
                              (first result)))))
                 entity
                 include))
       entities))))

(comment

  (def DB (build-inverted-indexes (read-data)))

  (search DB
          {:find    [:users :alias "Miss Dana"]
           :include [{:find_1 [:organizations :_id :organization_id]
                      :select {:name :organization_name}}
                     {:find   [:tickets :assignee_id :_id]
                      :select {:subject :subject}
                      :as     :assigned_tickets}
                     {:find   [:tickets :submitter_id :_id]
                      :select {:subject :subject}
                      :as     :submitted_tickets}]}))
