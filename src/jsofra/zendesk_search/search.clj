(ns jsofra.zendesk-search.search
  "Functions for creating search indexes for the Zendesk data."
  (:require [clojure.data.json :as json]
            [clojure.java.io :as io]))

(defn read-catalogue [path]
  (with-open [reader (io/reader (io/resource path))]
    (json/read reader :key-fn keyword)))

(defn read-catalogues []
  {:users         (read-catalogue "data/users.json")
   :organizations (read-catalogue "data/organizations.json")
   :tickets       (read-catalogue "data/tickets.json")})

(defn invert-entity
  [index entity]
  (reduce-kv (fn [m k v]
               (assoc m k (into {} (map vector (if (coll? v) v [v]) (repeat [index])))))
             {} entity))

(defn build-inverted-index [catalogue]
  (apply merge-with
         (partial merge-with into)
         (map-indexed invert-entity catalogue)))

(defn build-inverted-indexes [catalogues]
  {:catalogues       catalogues
   :inverted-indexes (reduce-kv (fn [m k catalogue]
                                  (assoc m k (build-inverted-index catalogue)))
                                {} catalogues)})

(defn lookup-entities
  [{:keys [catalogues inverted-indexes]} [catalogue-key field value]]
  (if-let [catalogue (get catalogues catalogue-key)]
    (if-let [field-indexes (get-in inverted-indexes [catalogue-key field])]
      (if value
        (map catalogue (get field-indexes value))
        (let [indexes (set (apply concat (vals field-indexes)))]
          (->> catalogue
               (map-indexed (fn [index entity]
                              (when (not (contains? indexes index))
                                entity)))
               (filter identity))))
      (throw (ex-info (format "Field '%s' not found not found in catalogue '%s'." field catalogue-key)
                      {:error   ::unknown-field
                       :context {:search      [catalogue-key field value]
                                 :know-fields (keys (get inverted-indexes catalogue-key))}})))
    (throw (ex-info (format "Catalogue '%s' not found." catalogue-key)
                    {:error   ::unknown-catalogue
                     :context {:search          [catalogue-key field value]
                               :know-catalogues (keys catalogues)}}))))

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
  ([inverted-index-map {:keys [find include select as]} parent]
   (let [[catalogue-key field value] find
         entities                  (->> [catalogue-key field (if parent (get parent value) value)]
                                        (lookup-entities inverted-index-map)
                                        (select-fields select))]
     (let [result (if include
                    (mapv
                     #(reduce
                       (fn [entity query]
                         (merge entity (search inverted-index-map query entity)))
                       %
                       include)
                     entities)
                    entities)]
       (if as
         {as result}
         (first result))))))

(comment

  (def DB (build-inverted-indexes (read-catalogues)))

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
