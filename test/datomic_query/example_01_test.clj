(ns datomic-query.example-01-test
  (:require
   [clojure.test :refer [deftest is testing]]
   [datomic-query.database :as database]
   [datomic-query.tasks :as tasks]
   [datomic.api :as d]
   [matcher-combinators.matchers :as m]
   [matcher-combinators.test :refer [match?]]))

(deftest teams-and-tasks-test
  (let [conn (database/recreate)
        _ @(d/transact conn tasks/schema)
        _ @(d/transact conn tasks/teams)
        _ @(d/transact conn tasks/tasks)
        db (d/db conn)]

    (testing "there are three teams, `Alpha`, `Beta` and `Gamma` (tuple format)"
      (is (match? #{["Alpha"] ["Beta"] ["Gamma"]}
                  (d/q '[:find ?name ;; the results is set of tuples
                         :where [_ :team/name ?name]] db))))

    (testing "there are three teams, `Alpha`, `Beta` and `Gamma` (vector format)"
      (is (match? (m/in-any-order ["Alpha" "Beta" "Gamma"])
                  (d/q '[:find [?name ...] ;; ... makes the returned data be a vector
                         :where [_ :team/name ?name]] db))))

    (testing "the business-unit of the `Gamma` team is `Omega`"
      (is (match? "Omega"
                  (d/q '[:find ?bu . ;; the dot notation represent a single value
                         :where
                         [?t :team/name "Gamma"]
                         [?t :team/business-unit ?bu]]
                       db))))))
