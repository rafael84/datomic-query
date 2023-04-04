(ns datomic-query.database 
  (:require
   [datomic.api :as d]))

(def uri-base "datomic:mem://")

(defn recreate
  "Create a connection to an anonymous, in-memory database."
  []
  (let [uri (str uri-base (d/squuid))]
    (d/delete-database uri)
    (d/create-database uri)
    (d/connect uri)))
