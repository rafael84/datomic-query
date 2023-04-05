(ns datomic-query.tasks-test
  (:require
   [clojure.test :refer [deftest is testing]]
   [datomic-query.database :as database]
   [datomic-query.inventory :as inventory]
   [datomic-query.tasks :as tasks]
   [datomic.api :as d]
   [matcher-combinators.matchers :as m]
   [matcher-combinators.test :refer [match?]]))

(deftest query-using-my-own-data-test
  (let [facts [[:bruna    :human?   true]
               [:bruna    :likes    "sushi"]
               [:enzo     :human?   false]
               [:enzo     :likes    "sushi"]
               [:rafa     :likes    "barbecue"]
               [:rafa     :human?   true]
               [:garfield :likes    "lasagna"]
               [:garfield :human?   false]]]
    (testing "rafa likes barbecue"
      (is (match? :rafa
                  (d/q '[:find ?who .
                         :where [?who :likes "barbecue"]] facts))))
    (testing "there are two humans"
      (is (match? 2
                  (d/q '[:find (count ?who) .
                         :where [?who :human? true]] facts))))
    (testing "sushi is the favorite food"
      (is (match? ["sushi"]
                  (d/q '[:find (max 1 ?food) .
                         :where [?who :likes ?food]] facts))))
    (testing "who loves lasagna OR is not human?"
      (is (match? [:garfield :enzo]
                  (d/q '[:find [?who ...]
                         :where (or [?who :likes "lasagna"]
                                    [?who :human? false])] facts))))))

;;
;; BASICS
;;
;;         Datomic is a database system that stores data as immutable facts,
;;         which are called "datoms".
;;
;;         A datom represents a piece of information in the form of a tuple
;;         with four components:
;;
;;           - Entity ID:       a unique identifier for the entity that the
;;                              datom pertains to.
;;
;;           - Attribute ID:    a unique identifier for the attribute that
;;                              the datom pertains to.
;;
;;           - Value:           the value of the attribute for the entity.
;;
;;           - Transaction ID:  a unique identifier for the transaction that
;;                              added the datom to the database.
;;
;;         The combination of these four components forms a unique identifier
;;         for each datom in the database.
;;
;;         Datoms are always added to the database as part of a transaction,
;;         and once added, they cannot be modified or deleted.
;;
;; DATOMS
;;
;;        +--------+-----------------+---------------------------+--------+
;;        | Entity | Attribute       | Value                     | Tx     |
;;        +--------+-----------------+---------------------------+--------+
;;        | 1      | :db/ident       | :task/title               | 1000   |
;;        | 2      | :db/ident       | :task/description         | 1000   |
;;        | 3      | :db/ident       | :task/completed           | 1000   |
;;        | 4      | :db/ident       | :task/due-date            | 1000   |
;;        | 5      | :db/ident       | :task/priority            | 1000   |
;;        | 1001   | :task/title     | "Task 1"                  | 2000   |
;;        | 1001   | :task/completed | false                     | 2000   |
;;        | 1001   | :task/due-date  | "2023-05-01T10:00:00.000" | 2000   |
;;        | 1001   | :task/priority  | :high                     | 2000   |
;;        | 1002   | :task/title     | "Task 2"                  | 3000   |
;;        | 1002   | :task/completed | true                      | 3000   |
;;        | 1002   | :task/due-date  | "2023-05-05T14:00:00.000" | 3000   |
;;        | 1002   | :task/priority  | :medium                   | 3000   |
;;        +--------+-----------------+---------------------------+--------+
;;

(deftest basics-test
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
    ;;
    ;;     :find ?a ?b	      relation	      Collection of Lists
    ;;     :find [?a …]	      collection	    Collection
    ;;     :find [?a ?b]	    single tuple	  List
    ;;     :find ?a .	        single scalar 	Scalar Value

    ;;
    ;; The `relation` find spec is the most common, and the most general.
    ;;
    ;; It will return a tuple for each result, with values in each tuple
    ;; matching the named variables.
    ;;

    (testing "there are three `teams`: `Alpha`, `Beta` and `Gamma` (set of tuples format)"
      (is (match? #{["Alpha"]
                    ["Beta"]
                    ["Gamma"]}
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
    ;; The `collection` find spec is useful when you are only interested in a single variable.
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
    ;; The `single tuple` find spec is useful when you are interested in multiple variables,
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
    ;; The `scalar` find spec is useful when you want to return a single value
    ;; of a single variable.
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
                       db))))))

(deftest parameterized-query-test
  (let [conn (database/recreate)
        _ @(d/transact conn tasks/schema)
        _ @(d/transact conn tasks/teams)
        _ @(d/transact conn tasks/tasks)
        db (d/db conn)]

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
                           [:medium true]]))))))

(deftest function-expressions-test
  (let [conn (database/recreate)
        _ @(d/transact conn tasks/schema)
        _ @(d/transact conn tasks/teams)
        _ @(d/transact conn tasks/tasks)
        db (d/db conn)]

    (testing "task with 12 or less characters in its title"
      (is (match? "Mow the lawn"
                  (d/q '[:find ?title .
                         :where
                         [?task :task/title ?title]
                         [(count ?title) ?title-size]
                         [(<= ?title-size 12)]] ;; cannot be nested
                       db))))))

;;
;; Datomic Pull
;;
;; Pull is a declarative way to make hierarchical (and possibly nested) selections
;; of information about entities.
;;
;; Pull applies a pattern to a collection of entities, building a map for each entity.
;;

(deftest pull-test
  (let [conn (database/recreate)
        _ @(d/transact conn inventory/schema)
        _ @(d/transact conn inventory/inventory)
        db (d/db conn)]

    (testing "pull black shirt data"
      (is (match? {:inventory/sku "black-shirt"
                   :inventory/colors [{:db/id int?}]
                   :inventory/type {:db/id int?}
                   :inventory/price 10}
                  (d/q '[:find (pull ?i [*]) .
                         :where [?i :inventory/sku "black-shirt"]] db))))

    (testing "pull black shirt data, with references"
      (is (match? {:inventory/sku "black-shirt"
                   :inventory/colors [{:db/ident :black}]
                   :inventory/type {:db/ident :shirt}
                   :inventory/price 10}
                  (d/q '[:find (pull ?i [*
                                         {:inventory/type [:db/ident]}
                                         {:inventory/colors [:db/ident]}]) .
                         :where [?i :inventory/sku "black-shirt"]] db))))

    (testing "pull purple white shirt data, with references"
      (is (match? {:inventory/sku "purple-white-shirt"
                   :inventory/colors (m/in-any-order [{:db/ident :purple}
                                                      {:db/ident :white}])
                   :inventory/type {:db/ident :shirt}
                   :inventory/price 25}
                  (d/q '[:find (pull ?i [*
                                         {:inventory/type [*]}
                                         {:inventory/colors [*]}]) .
                         :where [?i :inventory/sku "purple-white-shirt"]] db))))

    (testing "pull white pants data, with references, using `d/pull`"
      (is (match? {:inventory/sku "white-pants"
                   :inventory/colors [{:db/ident :white}]
                   :inventory/type {:db/ident :pants}
                   :inventory/price 30}
                  (d/pull db
                          '[* {:inventory/type [:db/ident]}
                            {:inventory/colors [:db/ident]}]
                          [:inventory/sku "white-pants"]))))))

(deftest reverse-lookup-test
  (let [conn (database/recreate)
        _ @(d/transact conn tasks/schema)
        _ @(d/transact conn tasks/teams)
        _ @(d/transact conn tasks/tasks)
        db (d/db conn)]
    (testing "reverse lookup"
      (is (match? {:team/business-unit "Sigma"
                   :task/_team [#:task{:title "Do the laundry"   :priority :medium :completed true}
                                #:task{:title "Iron the clothes" :priority :low    :completed false}
                                #:task{:title "Water the plants" :priority :high   :completed false}
                                #:task{:title "Mow the lawn"     :priority :medium :completed false}]}
                  (d/q '[:find (pull ?team [:team/business-unit
                                            {:task/_team [:task/title :task/priority :task/completed]}]) .
                         :where [?team :team/name "Beta"]] db))))))

(deftest history-test
  (let [conn (database/recreate)
        _ @(d/transact conn tasks/schema)
        _ @(d/transact conn tasks/teams)
        _ @(d/transact conn tasks/tasks)
        task-title "Water the plants"
        task-id (d/q '[:find ?t .
                       :in $ ?task-title
                       :where [?t :task/title ?task-title]]
                     (d/db conn) task-title)]

    (testing "the only record is: task is not completed"
      (is (match? [int? false]
                  (d/q '[:find [?tx ?v]
                         :in $ ?task-title
                         :where
                         [?task :task/title ?task-title]
                         [?task :task/completed ?v ?tx]]
                       (d/history (d/db conn)) task-title))))

    (testing "there are three records and the last one states that the task is completed"
      (let [_ @(d/transact conn [[:db/add task-id :task/completed true]])]
        (is (match? [[inst? false true]   ;; t+0 completed is false (added)
                     [inst? false false]  ;; t+1 completed is false (retracted)
                     [inst? true true]]   ;; t+2 completed is true (added)
                    (->> (d/q '[:find ?when ?v ?op
                                :in $ ?task-title
                                :where
                                [?task :task/title ?task-title]
                                [?task :task/completed ?v ?tx ?op]
                                [?tx :db/txInstant ?when]]
                              (d/history (d/db conn)) task-title)
                         (sort-by first))))))))
