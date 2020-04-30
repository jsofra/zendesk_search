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

(defn invert-entity
  [index entity]
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
  (when-let [field-indexes (get-in inverted-indexes [entity-type field])]
    (if value
      (map (get entities entity-type) (get field-indexes value))
      (let [indexes (set (apply concat (vals field-indexes)))]
        (->> (get entities entity-type)
             (map-indexed (fn [index entity]
                            (when (not (contains? indexes index))
                              entity)))
             (filter identity))))))

(defn select-fields [selection entities]
  (if selection
    (mapv (fn [entity]
            (-> entity
                (select-keys (keys selection))
                (clojure.set/rename-keys selection)))
          entities)
    entities))

(defn search
  "
  Recursively query for entities in the index.
  "
  ([inverted-index-map query]
   (search inverted-index-map query nil))
  ([inverted-index-map {:keys [find include select]} parent]
   (let [[entity-type field value] find
         entities                  (->> [entity-type field (if parent (get parent value) value)]
                                        (lookup-entities inverted-index-map)
                                        (select-fields select))]
     (if include
       (mapv #(reduce (fn [entity {:keys [find as] :as query}]
                        (let [result (search inverted-index-map query entity)]
                         (merge entity
                                (if as
                                  {as result}
                                  (first result)))))
                     %
                     include)
             entities)
       entities))))

(comment

  (def DB (build-inverted-indexes (read-data)))

  (def R (search DB
                 {:find    [:users :alias "Miss Dana"]
                  :include [{:find   [:organizations :_id :organization_id]
                             :select {:name :organization_name}}
                            {:find   [:tickets :assignee_id :_id]
                             :select {:_id     :ticket_id
                                      :subject :subject}
                             :as     :assigned_tickets}
                            {:find   [:tickets :submitter_id :_id]
                             :select {:_id     :ticket_id
                                      :subject :subject}
                             :as     :submitted_tickets}]}))

  )
