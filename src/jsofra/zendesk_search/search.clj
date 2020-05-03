(ns jsofra.zendesk-search.search
  "Functions for creating search indexes for the Zendesk data.")

(defn normalize-value [value]
  (clojure.string/lower-case (str value)))

(defn analyze-value [analyzer value]
  (if analyzer
    (map normalize-value (concat [value] (re-seq (re-pattern analyzer) value)))
    [(normalize-value value)]))

(defn analyze-values [analyzer values]
  (mapcat (partial analyze-value analyzer)
          (filter (complement coll?)
                  (rest (tree-seq coll? seq values)))))

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
  (let [normalized-value (normalize-value value)]
    (if-let [{:keys [entities]} (get catalogues catalogue-key)]
      (if-let [field-indexes (get-in inverted-indexes [catalogue-key field])]
        (if (seq normalized-value)
          (map entities (get field-indexes normalized-value))
          (let [indexes (set (apply concat (vals field-indexes)))]
            (->> entities
                 (map-indexed (fn [index entity]
                                (when (not (contains? indexes index))
                                  entity)))
                 (filter identity))))
        (throw (ex-info (format "Field '%s' not found not found in catalogue '%s'." field (name catalogue-key))
                        {:error   ::unknown-field
                         :context {:search      [catalogue-key field value]
                                   :know-fields (keys (get inverted-indexes catalogue-key))}})))
      (throw (ex-info (format "Catalogue '%s' not found." (name catalogue-key))
                      {:error   ::unknown-catalogue
                       :context {:search          [catalogue-key field value]
                                 :know-catalogues (keys catalogues)}})))))

(defn select-fields [selection entities]
  (if selection
    (mapv (fn [entity]
            (-> entity
                (select-keys (keys selection))
                (clojure.set/rename-keys selection)))
          entities)
    entities))

;; The public interface

(defn has-catalogue? [{:keys [catalogues]} catalogue-key]
  (contains? catalogues (keyword catalogue-key)))

(defn has-field? [{:keys [inverted-indexes]} catalogue-key field]
  (contains? (get inverted-indexes (keyword catalogue-key)) field))

(defn list-catalogues [{:keys [catalogues]}]
  (sort (keys catalogues)))

(defn list-fields [{:keys [inverted-indexes]} catalogue-key]
  (sort (keys (get inverted-indexes catalogue-key))))

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
