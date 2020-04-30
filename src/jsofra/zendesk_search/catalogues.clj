(ns jsofra.zendesk-search.catalogues
  (:require [clojure.data.json :as json]
            [clojure.java.io :as io]))

(defn read-catalogue [path]
  (try
    (with-open [reader (io/reader path)]
      (json/read reader :key-fn keyword))
    (catch Exception e
      (throw (ex-info (format "Could not read catalogue from '%s'." path)
                      {:error   ::read-failure
                       :context {:path path}})))))

(defn read-catalogues [paths-map]
  (zipmap (keys paths-map)
          (->> (vals paths-map)
               (map #(future (read-catalogue %)))
               (map deref))))
