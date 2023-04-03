(ns datomic-query.core
  (:require
   [datomic.api :as d]))

(def schema [{:db/ident :task/title
              :db/valueType :db.type/string
              :db/cardinality :db.cardinality/one
              :db/doc "The title of a task in a todo list."}

             {:db/ident :task/due-date
              :db/valueType :db.type/instant
              :db/cardinality :db.cardinality/one
              :db/doc "The due date of a task in a todo list."}

             {:db/ident :task/completed
              :db/valueType :db.type/boolean
              :db/cardinality :db.cardinality/one
              :db/doc "The completion status of a task in a todo list."}

             {:db/ident :task/priority
              :db/valueType :db.type/keyword
              :db/cardinality :db.cardinality/one
              :db/doc "The priority of a task in a todo list."}

             {:db/ident :task.priority/low
              :db/valueType :db.type/keyword
              :db/cardinality :db.cardinality/one
              :db/doc "Low priority for a task in a todo list."}

             {:db/ident :task.priority/medium
              :db/valueType :db.type/keyword
              :db/cardinality :db.cardinality/one
              :db/doc "Medium priority for a task in a todo list."}

             {:db/ident :task.priority/high
              :db/valueType :db.type/keyword
              :db/cardinality :db.cardinality/one
              :db/doc "High priority for a task in a todo list."}])

(def tasks [{:db/id (d/tempid :db.part/user)
             :task/title "Buy groceries"
             :task/due-date #inst "2023-04-04T17:00:00.000"
             :task/completed false
             :task/priority :task.priority/high}

            {:db/id (d/tempid :db.part/user)
             :task/title "Finish project report"
             :task/due-date #inst "2023-04-10T23:59:59.000"
             :task/completed false
             :task/priority :task.priority/medium}

            {:db/id (d/tempid :db.part/user)
             :task/title "Call the plumber"
             :task/due-date #inst "2023-04-05T12:00:00.000"
             :task/completed false
             :task/priority :task.priority/low}])

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
  @(d/transact conn tasks)

  (d/q '[:find ?title
         :where [?t :task/title ?title]]
       (d/db conn))

  :rc)
