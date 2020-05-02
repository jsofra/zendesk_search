(ns jsofra.zendesk-search.catalogues
  (:require [clojure.data.json :as json]
            [clojure.edn :as edn]
            [clojure.java.io :as io]))

(defn read-catalogue [path]
  (try
    (with-open [reader (io/reader path)]
      (json/read reader))
    (catch Exception e
      (throw (ex-info (format "Could not read catalogue from '%s'." path)
                      {:error   ::read-failure
                       :context {:path path}}
                      e)))))

(defn read-catalogues [catalogues]
  (reduce-kv (fn [m k catalogue]
                (assoc m k (assoc catalogue
                                  :entities
                                  (read-catalogue (:path catalogue)))))
              {} catalogues))

(defn read-config [path]
  (try
    (with-open [r (io/reader path)]
      (edn/read (java.io.PushbackReader. r)))
    (catch java.io.IOException e
      (throw (ex-info (format "Could not read catalogue config from '%s'." path)
                      {:error   ::read-failure
                       :context {:path path}}
                      e)))
    (catch RuntimeException e
      (throw (ex-info (format "Could not parse catalogue config from '%s'." path)
                      {:error   ::parse-failure
                       :context {:path path}}
                      e)))))

(defn build-query [catalogues {:keys [catalogue field value]}]
  (clojure.walk/postwalk-replace {:field? field :value? value}
                                 (get-in catalogues [(keyword catalogue) :query])))
