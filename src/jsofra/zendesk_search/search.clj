(ns jsofra.zendesk-search.search
  "Functions for creating search indexes for the Zendesk data.")

(defn normalize-value
  "
  Normalize all values for searching with by converting them to strings and lower casing them.
  This allows search to be case insensitive.
  "
  [value]
  (clojure.string/lower-case (str value)))

(defn analyze-values
  "
  Recursively flatten values. Normalize them, and apply regex analysers to them.
  The regex analysers will produce a seq of new values, they will also be flattened.
  "
  [analyzer values]
  (if (coll? values)
    (mapcat (partial analyze-values analyzer) values)
    (if analyzer
      (map normalize-value (concat [values] (re-seq (re-pattern analyzer) values)))
      [(normalize-value values)])))

(defn invert-entity
  "
  Inverts an entity ready to be merged into a larger inverted index.
  Inversion here means that that given a field and value we can do a constant time lookup in hashmaps of
  the given 'index' for the entity.

  The 'entity' is converted it from a map:

  field->value/s to field->analyzed-values->[index]
"
  [analyzers index entity]
  (reduce-kv (fn [m k v]
               (assoc m k (into {} (map vector
                                        (analyze-values (get analyzers k) [v])
                                        (repeat [index])))))
             {} entity))

(defn build-inverted-index
  "
  Given a seq of 'entities' build an inverted index by inverting each one seperately and merging them together.
  This is a linear operation an may get quite slow given a large number of 'entities'.
  "
  [{:keys [entities analyzers]}]
  (apply merge-with
         (partial merge-with into)
         (map-indexed (partial invert-entity analyzers) entities)))

(defn build-inverted-indexes
  "Builds a set of inverted indexes (a database) by building an index for each catalogue (set of entites) in a map."
  [catalogues]
  {:catalogues       catalogues
   :inverted-indexes (reduce-kv (fn [m k catalogue]
                                  (assoc m k (build-inverted-index catalogue)))
                                {} catalogues)})

(defn lookup-entities
  "
  Given a 'catalogue-key', 'field' and 'value', looks up a seq of matching entities in the index.
  If any of the search terms are not found an empty seq is returned.

  Non-recursive, this is a constant time operation.
  "
  [{:keys [catalogues inverted-indexes]} [catalogue-key field value]]
  (let [normalized-value (normalize-value value)]
    (let [{:keys [entities]} (get catalogues catalogue-key)
          field-indexes      (get-in inverted-indexes [catalogue-key field])]
      (if (seq normalized-value)
        (map entities (get field-indexes normalized-value))
        (let [indexes (set (apply concat (vals field-indexes)))]
          (->> entities
               (map-indexed (fn [index entity]
                              (when (not (contains? indexes index))
                                entity)))
               (filter identity)))))))

(defn select-fields
  "
  Returns a seq of entities which each have only the keys in the 'replacement-map'.
  Their keys are also replaced by the corresponding values in 'replacement-map'.
  "
  [replacement-map entities]
  (if replacement-map
    (mapv (fn [entity]
            (-> entity
                (select-keys (keys replacement-map))
                (clojure.set/rename-keys replacement-map)))
          entities)
    entities))

(defn has-catalogue?
  "Is the give 'catalogue-key' in the database (inverted indexes)?"
  [{:keys [catalogues]} catalogue-key]
  (contains? catalogues (keyword catalogue-key)))

(defn has-field?
  "Is the give 'catalogue-key' and 'field' pair in the database (inverted indexes)?"
  [{:keys [inverted-indexes]} catalogue-key field]
  (contains? (get inverted-indexes (keyword catalogue-key)) field))

(defn list-catalogues [{:keys [catalogues]}]
  (sort (keys catalogues)))

(defn list-fields [{:keys [inverted-indexes]} catalogue-key]
  (sort (keys (get inverted-indexes catalogue-key))))

(defn query
  "
  Recursively query for entities in the index.

  The query has a structure like this:

  {:find    [<catalogue> <field> <value>]
   :include [{:find [<foreign-catalogue> <foreign-field> <join-value>]
              :select (optional <sub-replacement-map>)
              :as (optional <sub-result-alias>)}]
   :select  <replacement-map>
   :as      <result-alias>}

  Where :find is required and all other keys are optional.

  The :include is a vector of sub-queries with the same structure.
  With the one difference being that they will lookup their value (join-value) in the parent entity.
  This is how relationships are resolved.

  The returned value for a query will be:

  {<result-alias> [<entity>]}

  If the :as value is left out the result will be assumed to be a single value thus just the first value will be returned.
  For sub queries that value will be merged into the parent.
  "
  ([database query]
   (search database query nil))
  ([database {:keys [find include select as]} parent]
   (let [[catalogue-key field value] find
         entities                  (->> [catalogue-key field (if parent (get parent value) value)]
                                        (lookup-entities database)
                                        (select-fields select))]
     (let [result (if include
                    (mapv
                     #(reduce
                       (fn [entity query]
                         (merge entity (search database query entity)))
                       %
                       include)
                     entities)
                    entities)]
       (if as
         {as result}
         (first result))))))
