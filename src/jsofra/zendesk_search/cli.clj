(ns jsofra.zendesk-search.cli
  (:require [jsofra.zendesk-search.catalogues :as catalogues]
            [jsofra.zendesk-search.search :as search]
            [fipp.edn :as fipp])
  (:gen-class))

(def welcome-message
  (clojure.string/join
   "\n"
   ["Welcome to Zendesk Search!"
    "Your data is being prepared, it may take a moment to be ready."]))

(defn pause-message [_ _]
  "Press 'enter' to continue")

(defn options-message [_ _]
  (clojure.string/join
   "\n"
   ["------------------------------------------------------------"
    "Select search options:"
    " * s) to search the Zendesk catalogues"
    " * q) to quit"]))

(defn options-response [_ _ input]
  {:next (case input
           "s" :search-catalogue
           "q" :quit
           :options)})

(defn search-catalogue-message [db _]
  (clojure.string/join
   "\n"
   (concat ["Enter the catalogue you would like to search in:"]
           (map #(str " * " (name %)) (search/list-catalogues db)))))

(defn search-catalogue-error-message [catalogue]
  (format "No catalogue with the name '%s' found." catalogue))

(defn search-catalogue-response [db params input]
  (if (search/has-catalogue? db input)
    {:params (assoc params :catalogue input)
     :next   :search-field}
    {:params {:error-message (search-catalogue-error-message input)}
     :next   :error}))

(defn search-field-message [db {:keys [catalogue]}]
  (clojure.string/join
   "\n"
   (concat [(format "Enter the field to search for within '%s':" catalogue)]
           (map #(str " * " (name %)) (search/list-fields db (keyword catalogue))))))

(defn search-field-error-message [catalogue field]
  (format "No field with the name '%s' found in catalogue '%s'." field catalogue))

(defn search-field-response [db {:keys [catalogue] :as params} input]
  (if (search/has-field? db catalogue input)
    {:params (assoc params :field input)
     :next   :search-value}
    {:params {:error-message (search-field-error-message catalogue input)}
     :next   :error}))

(defn search-value-message [db {:keys [field catalogue]}]
  (format "Enter a value of field '%s' to search for in '%s':" field catalogue))

(defn search-value-response [_ params input]
  {:params (assoc params :value input)
   :next   :search-results})

(defn search-results-message [db {:keys [catalogue field value] :as params}]
  (let [query   (catalogues/build-query (:catalogues db) params)
        results (get (search/query db query) (keyword (:catalogue params)))]
    (if (seq results)
      (with-out-str (fipp/pprint results))
      (format "No results found for '%s' of field '%s' in '%s'." value field catalogue))))

(defn error-message [_ {:keys [error-message]}] error-message)

(def quit-message "Thank you for using Zendesk Search, bye!")

(defn quit []
  (println "Thank you for using Zendesk Search, bye!")
  (System/exit 1))

(def cli-state-machine
  {:pause            {:message  pause-message
                      :response (fn [_ _ _] {:next :options})}
   :options          {:message  options-message
                      :response options-response}
   :search-catalogue {:message  search-catalogue-message
                      :response search-catalogue-response}
   :search-field     {:message  search-field-message
                      :response search-field-response}
   :search-value     {:message  search-value-message
                      :response search-value-response}
   :search-results   {:message  search-results-message
                      :response (fn [_ _ _] {:next :options})}
   :error            {:message  error-message
                      :response (fn [_ _ _] {:next :options})}})

(defn run-cli-loop!
  "
  Continuously loop through the `cli-state-machine`:
   * print :message
   * capture input
   * run :response
   * got to the next state returned be :response
  "
  [cli-states db start-state]
  (loop [state  start-state
         params {}]
    (let [{:keys [message response]} (get cli-states state)]
      (do
        (try
          (println (message db params) "\n")
          (catch Exception e
            (println (ex-message e) "\n")))
        (flush)
        (let [{:keys [next params]} (response db params (read-line))]
          (if (= next :quit)
            (quit)
            (recur next params)))))))

(defn -main [& args]
  (try
    (let [db (-> ".zendesk-search/catalogues.edn"
                 catalogues/read-config
                 catalogues/read-catalogues
                 search/build-inverted-indexes)]
      (println welcome-message "\n")
      (run-cli-loop! cli-state-machine db :pause))
    (catch Exception e
      (println (ex-message e)))))
