(ns jsofra.zendesk-search.cli
  (:require [jsofra.zendesk-search.catalogues :as catalogues]
            [jsofra.zendesk-search.search :as search]
            [clojure.tools.cli :as cli])
  (:gen-class))

(def welcome-message
  (clojure.string/join
   "\n"
   ["Welcome to Zendesk Search!"
    "Your data is being prepared, it may take a moment to be ready."]))

(defn exit-message [_ _]
  "Thank you for using Zendesk Search, bye!")

(defn pause-message [_ _]
  "Press 'enter' to continue")

(defn options-message [_ _]
  (clojure.string/join
   "\n"
   ["Select search options:"
    " * s) to search the Zendesk catalogues"
    " * q) to quit"]))

(defn options-response [_ _ input]
  {:next (case input
           "s" :search-catalogue
           "q" :quit
           :options)})

(defn search-catalogue-message [{:keys [db]} _]
  (clojure.string/join
   "\n"
   (concat ["Enter the catalogue you would like to search for:"]
           (map #(str " * " (name %)) (search/list-catalogues db)))))

(defn search-catalogue-response [_ _ input]
  {:params {:catalogue input}
   :next   :search-field})

(defn search-field-message [{:keys [db]} {:keys [catalogue]}]
  (clojure.string/join
   "\n"
   (concat [(format "Enter the field to search for within %s:" catalogue)]
           (map #(str " * " (name %)) (search/list-fields db (keyword catalogue))))))

(defn search-field-response [_ _ input]
  {:params {:field input}
   :next   :pause})

(def cli-state-machine
  {:pause            {:message  pause-message
                      :response (fn [_ _ _] {:next :options})}
   :options          {:message  options-message
                      :response options-response}
   :search-catalogue {:message  search-catalogue-message
                      :response search-catalogue-response}
   :search-field     {:message  search-field-message
                      :response search-field-response}
   :quit             {:message  exit-message
                      :response (fn [_ _ _] (System/exit 1))}})

(defn run-cli-loop! [cli-states system]
  (loop [state  :pause
         params nil]
    (let [{:keys [message response]} (get cli-states state)]
      (do
        (println (message system params) "\n")
        (flush)
        (let [{:keys [next params]} (response system params (read-line))]
          (recur next params))))))

(defn -main [& args]
  (let [config (catalogues/read-config "./catalogues.edn")
        db     (search/build-inverted-indexes (catalogues/read-catalogues config))]
    (println welcome-message "\n")
    (run-cli-loop! cli-state-machine {:config config :db db})))



(comment

  (def C (catalogues/read-catalogues (catalogues/read-config "./catalogues.edn")))

  (def DB (search/build-inverted-indexes C))

  (def R (search/search DB
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
                         :as      :users}))

  )
