(ns datomic-query.tasks-test
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

    ;;
    ;; Find Specifications
    ;;
    ;; Where bindings control inputs, find specifications control results.
    ;;
    ;;     Find Spec	        Returns	        Java Type Returned
    ;;     :find ?a ?b	      relation	      Collection of Lists
    ;;     :find [?a …]	      collection	    Collection
    ;;     :find [?a ?b]	    single tuple	  List
    ;;     :find ?a .	        single scalar 	Scalar Value

    ;;
    ;; The relation find spec is the most common, and the most general.
    ;;
    ;; It will return a tuple for each result, with values in each tuple
    ;; matching the named variables.
    ;;

    (testing "there are three `teams`: `Alpha`, `Beta` and `Gamma` (set of tuples format)"
      (is (match? #{["Alpha"] ["Beta"] ["Gamma"]}
                  (d/q '[:find ?name
                         :where [_ :team/name ?name]] db))))

    (testing "the `business-unit` of the `Gamma` team is `Omega` (single value inside a set of tuples)"
      (is (match? #{["Omega"]}
                  (d/q '[:find ?bu
                         :where
                         [?t :team/name "Gamma"]
                         [?t :team/business-unit ?bu]]
                       db))))

    ;;
    ;; The collection find spec is useful when you are only interested in a single variable.
    ;;

    (testing "there are three `teams`: `Alpha`, `Beta` and `Gamma` (vector format)"
      (is (match? (m/in-any-order ["Alpha" "Beta" "Gamma"])
                  (d/q '[:find [?name ...]
                         :where [_ :team/name ?name]] db))))

    (testing "tasks with `due-date`"
      (is (match? (m/in-any-order ["Clean the windows"
                                   "Mow the lawn"
                                   "Sweep the floor"])
                  (d/q '[:find [?title ...]
                         :where
                         [?t :task/due-date]
                         [?t :task/title ?title]]
                       db))))

    ;;
    ;; The single tuple find spec is useful when you are interested in multiple variables,
    ;; but expect only a single result.
    ;;
    ;; The form [?priority ?due-date ?completed] below returns a single triple,
    ;; not wrapped in a relation.
    ;;

    (testing "a summary of the `Sweep the floor` task"
      (is (match? [:low #inst "2023-11-15" false]
                  (d/q '[:find [?priority ?due-date ?completed]
                         :where
                         [?t :task/title "Sweep the floor"]
                         [?t :task/priority ?priority]
                         [?t :task/due-date ?due-date]
                         [?t :task/completed ?completed]]
                       db))))

    ;;
    ;; The scalar find spec is useful when you want to return a single value
    ;; of a single variable. The form `?bu` below returns a single scalar value:
    ;;

    (testing "the `business-unit` of the `Gamma` team is `Omega` (single value)"
      (is (match? "Omega"
                  (d/q '[:find ?bu .
                         :where
                         [?t :team/name "Gamma"]
                         [?t :team/business-unit ?bu]]
                       db))))

    (testing "tasks for `2024`"
      (is (match? ["Clean the windows"]
                  (d/q '[:find [?title ...]
                         :where
                         [?t :task/due-date ?due-date]
                         [(>= ?due-date #inst "2024")]
                         [?t :task/title ?title]]
                       db))))

    (testing "completed tasks for team `Alpha`"
      (is (match? (m/in-any-order ["Dust the shelves"
                                   "Wash the dishes"])
                  (d/q '[:find [?title ...]
                         :where
                         [?t :task/team [:team/name "Alpha"]]
                         [?t :task/completed true]
                         [?t :task/title ?title]]
                       db))))

    (testing "pending tasks"
      (is (match? 7
                  (d/q '[:find (count ?t) .
                         :where [?t :task/completed false]]
                       db))))

    (testing "pending tasks by business-unit"
      (is (match? [["Omega" 2]
                   ["Sigma" 4]]
                  (d/q '[:find ?bu (count ?task)
                         :where
                         [?task :task/completed false]
                         [?task :task/team ?team]
                         [?team :team/business-unit ?bu]]
                       db))))

    (testing "high priority tasks"
      (is (match? (m/in-any-order ["Water the plants"
                                   "Dust the shelves"
                                   "Clean the bathroom"])
                  (d/q '[:find [?title ...]
                         :in $ ?priority
                         :where
                         [?task :task/priority ?priority]
                         [?task :task/title ?title]]
                       db :high))))

    ;;
    ;; Not Clauses
    ;;
    ;; not clauses allow you to express that one or more logic variables inside
    ;; a query must not satisfy all of a set of predicates.
    ;;

    (testing "total unassigned tasks"
      (is (match? 2
                  (d/q '[:find (count ?task) .
                         :where
                         [?task :task/title]
                         (not [?task :task/team])]
                       db))))

    (testing "teams without high priority tasks"
      (is (match? "Gamma"
                  (d/q '[:find ?team-name .
                         :where
                         [?team :team/name ?team-name]
                         (not-join [?team]
                                   [?task :task/team ?team]
                                   [?task :task/priority :high])]
                       db))))

    ;;
    ;; Collection Binding
    ;;
    ;; A collection binding binds a single variable to multiple values
    ;; passed in as a collection. This can be used to ask "or" questions
    ;;
    ;; https://docs.datomic.com/on-prem/query/query.html#collection-binding
    ;;
    (testing "tasks for teams `Beta` OR `Gamma`"
      (is (match? 6
                  (d/q '[:find (count ?task) .
                         :in $ [?teams ...]
                         :where
                         [?task :task/team ?team]
                         [?team :team/name ?teams]]
                       db ["Beta" "Gamma"]))))

    ;;
    ;; Relation Binding
    ;;
    ;; A relation binding is fully general, binding multiple variables positionally to a
    ;; relation (collection of tuples) passed in.
    ;;
    ;; This can be used to ask "or" questions involving multiple variables.
    ;;
    ;; https://docs.datomic.com/on-prem/query/query.html#relation-binding
    ;;
    (testing "(pending AND low priority) OR (completed AND medium priority) tasks"
      (is (match? #{["Gamma" "Sweep the floor" false]
                    ["Beta" "Iron the clothes" false]
                    ["Beta" "Do the laundry" true]}
                  (d/q '[:find ?team-name ?title ?completed
                         :in $ [[?priority ?completed]]
                         :where
                         [?task :task/priority ?priority]
                         [?task :task/completed ?completed]
                         [?task :task/title ?title]
                         [?task :task/team ?team]
                         [?team :team/name ?team-name]]
                       db [[:low false]
                           [:medium true]]))))

    :rc))

