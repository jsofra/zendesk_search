(ns jsofra.zendesk-search.search
  "Functions for creating search indexes for the Zendesk data.")


(defn normalize-value [value]
  (-> value
      str
      clojure.string/lower-case))

(defn analyze-value [analyzer value]
  (if analyzer
    (map normalize-value
         (concat [value] (re-seq (re-pattern analyzer) value)))
    [(normalize-value value)]))

(defn analyze-values [analyzer values]
  (->> (tree-seq coll? seq values)
       rest
       (filter (complement coll?))
       (mapcat (partial analyze-value analyzer))))

(defn invert-entity
  [analyzers index entity]
  (reduce-kv (fn [m k v]
               (assoc m k (into {} (map vector
                                        (analyze-values (get analyzers k) [v])
                                        (repeat [index])))))
             {} entity))

(defn build-inverted-index [{:keys [entities analyzers]}]
  (apply merge-with
         (partial merge-with into)
         (map-indexed (partial invert-entity analyzers) entities)))

(defn build-inverted-indexes [catalogues]
  {:catalogues       catalogues
   :inverted-indexes (reduce-kv (fn [m k catalogue]
                                  (assoc m k (build-inverted-index catalogue)))
                                {} catalogues)})

(defn lookup-entities
  [{:keys [catalogues inverted-indexes]} [catalogue-key field value]]
  (if-let [{:keys [entities]} (get catalogues catalogue-key)]
    (if-let [field-indexes (get-in inverted-indexes [catalogue-key field])]
      (if value
        (map entities (get field-indexes (normalize-value value)))
        (let [indexes (set (apply concat (vals field-indexes)))]
          (->> entities
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

  (require '[jsofra.zendesk-search.catalogues :as catalogues])


  (def C (catalogues/read-catalogues (catalogues/read-config "./catalogues.edn")))

  (def DB (build-inverted-indexes C))

  (def R (search DB
                 {:find    [:users "created_at" "2016-04-18"]
                  :include [{:find   [:organizations "_id" "organization_id"]
                             :select {"name" "organization_name"}}
                            {:find   [:tickets "assignee_id" "_id"]
                             :select {"_id"     "ticket_id"
                                      "subject" "subject"}
                             :as     "assigned_tickets"}
                            {:find   [:tickets "submitter_id" "_id"]
                             :select {"_id"     "ticket_id"
                                      "subject" "subject"}
                             :as     "submitted_tickets"}]
                  :as :users}))

  )
