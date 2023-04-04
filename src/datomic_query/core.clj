(ns datomic-query.core
  (:require
   [datomic.api :as d]))

(def schema [{:db/ident       :team/name
              :db/unique      :db.unique/identity
              :db/valueType   :db.type/string
              :db/cardinality :db.cardinality/one}

             {:db/ident       :team/tasks
              :db/valueType   :db.type/ref
              :db/cardinality :db.cardinality/many}

             {:db/ident :task/title     :db/valueType :db.type/string  :db/cardinality :db.cardinality/one}
             {:db/ident :task/due-date  :db/valueType :db.type/instant :db/cardinality :db.cardinality/one}
             {:db/ident :task/completed :db/valueType :db.type/boolean :db/cardinality :db.cardinality/one}
             {:db/ident :task/priority  :db/valueType :db.type/keyword :db/cardinality :db.cardinality/one}])

(def teams [{:team/name "Alpha"}
            {:team/name "Beta"}])

(def tasks [{:task/title "Buy groceries"
             :task/due-date #inst "2023-04-04T17:00:00.000"
             :task/completed false
             :task/priority :high
             :team/tasks [[:team/name "Alpha"]]}

            {:task/title "Finish project report"
             :task/due-date #inst "2023-04-10T23:59:59.000"
             :task/completed false
             :team/tasks [[:team/name "Alpha"]]}

            {:task/title "Call the plumber"
             :task/due-date #inst "2023-04-05T12:00:00.000"
             :task/completed false
             :team/tasks [[:team/name "Beta"]]}])

(def db-uri-base "datomic:mem://")

(defn recreate-db
  "Create a connection to an anonymous, in-memory database."
  []
  (let [uri (str db-uri-base (d/squuid))]
    (d/delete-database uri)
    (d/create-database uri)
    (d/connect uri)))

(comment
  (def conn (recreate-db))
  @(d/transact conn schema)

  @(d/transact conn teams)
  @(d/transact conn tasks)

  (d/q '[:find ?t ?name
         :where [?t :team/name ?name]]
       (d/db conn))

  (d/q '[:find ?title
         :where [?t :task/title ?title]]
       (d/db conn))

  (d/q '[:find (pull ?t [* {:team/tasks [:team/name]}])
         :where [?t :task/title]]
       (d/db conn))

  (d/q '[:find (pull ?t [* {:team/tasks [:team/name]}])
         :where [?t :team/name]]
       (d/db conn))
  :rc)
