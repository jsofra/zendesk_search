(ns jsofra.zendesk-search.catalogues
  (:require [clojure.data.json :as json]
            [clojure.java.io :as io]))

(defn read-catalogue [path]
  (try
    (with-open [reader (io/reader path)]
      (json/read reader))
    (catch Exception e
      (throw (ex-info (format "Could not read catalogue from '%s'." path)
                      {:error   ::read-failure
                       :context {:path path}})))))

(defn read-catalogues [paths-map]
  (reduce-kv (fn [m k path]
               (assoc m k (read-catalogue path)))
             {} paths-map))
