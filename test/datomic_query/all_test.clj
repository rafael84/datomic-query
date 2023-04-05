(ns datomic-query.all-test
  (:require
   [clojure.test :refer [deftest is testing]]
   [datomic-query.database :as database]
   [datomic-query.inventory :as inventory]
   [datomic-query.tasks :as tasks]
   [datomic.api :as d]
   [matcher-combinators.matchers :as m]
   [matcher-combinators.test :refer [match?]]))

;; -------------------------------------------------------------------------------------------------
;; DATABASE     In Datomic, a database comprised of a set of datoms.
;; -------------------------------------------------------------------------------------------------
;; DATOMS       An immutable atomic fact that represents the addition or retraction of a
;;              relation between an entity, an attribute, a value, and a transaction.
;;
;;              A datom is expressed as a five-tuple:
;;              * an entity id (E)
;;              * an attribute (A)
;;              * a value for the attribute (V)
;;              * a transaction id (Tx)
;;              * a boolean (Op) indicating whether the datom is being added or retracted
;;
;;              Example Datom:
;;              E     42
;;              A     :user/favorite-color
;;              V     :blue
;;              Tx    1234
;;              Op    true
;; -------------------------------------------------------------------------------------------------
;; ENTITIES     An entity is a set of datoms that are all about the same E.
;;
;;              Example Entity:
;;              E     A                        V      Tx    Op
;;              42    :user/favorite-color    :blue   1234  true
;;              42    :user/first-name        "John"  1234  true
;;              42    :user/last-name         "Doe"   1234  true
;;              42    :user/favorite-color    :green  4567  true
;;              42    :user/favorite-color    :blue   4567  false
;; -------------------------------------------------------------------------------------------------

;;
;; Find Specifications
;;

(deftest find-specifications-test
  (let [conn (database/recreate)
        _ @(d/transact conn tasks/schema)
        _ @(d/transact conn tasks/teams)
        _ @(d/transact conn tasks/tasks)
        db (d/db conn)]

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
                         [?t :team/business-unit ?bu]] db))))

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
                         [?t :task/title ?title]] db))))

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
                         [?t :task/completed ?completed]] db))))

    ;;
    ;; The `scalar` find spec is useful when you want to return a single value
    ;; of a single variable.
    ;;

    (testing "the `business-unit` of the `Gamma` team is `Omega` (single value)"
      (is (match? "Omega"
                  (d/q '[:find ?bu .
                         :where
                         [?t :team/name "Gamma"]
                         [?t :team/business-unit ?bu]] db))))

    (testing "tasks for `2024`"
      (is (match? ["Clean the windows"]
                  (d/q '[:find [?title ...]
                         :where
                         [?t :task/due-date ?due-date]
                         [(>= ?due-date #inst "2024")]
                         [?t :task/title ?title]] db))))

    (testing "completed tasks for team `Alpha`"
      (is (match? (m/in-any-order ["Dust the shelves"
                                   "Wash the dishes"])
                  (d/q '[:find [?title ...]
                         :where
                         [?t :task/team [:team/name "Alpha"]]
                         [?t :task/completed true]
                         [?t :task/title ?title]] db))))

    (testing "pending tasks"
      (is (match? 7
                  (d/q '[:find (count ?t) .
                         :where [?t :task/completed false]] db))))

    (testing "pending tasks by business-unit"
      (is (match? [["Omega" 2]
                   ["Sigma" 4]]
                  (d/q '[:find ?bu (count ?task)
                         :where
                         [?task :task/completed false]
                         [?task :task/team ?team]
                         [?team :team/business-unit ?bu]] db))))))

;;
;; Inputs
;;

(deftest inputs-test
  (let [conn (database/recreate)
        _ @(d/transact conn tasks/schema)
        _ @(d/transact conn tasks/teams)
        _ @(d/transact conn tasks/tasks)
        db (d/db conn)]

    ;;
    ;; Implicit Database
    ;;

    (testing "`$database` was there all the time"
      (is (match? 3
                  (d/q '[:find (count ?name) .
                         :in $database
                         :where [$database _ :team/name ?name]]
                       db ;; this is $database
                       ))))

    (testing "when there is just one `$database`, often we use just `$`"
      (is (match? 3
                  (d/q '[:find (count ?name) .
                         :in $
                         :where [$ _ :team/name ?name]]
                       db ;; this is $
                       ))))

    (testing "which can be totally omitted with you have no extra dbs or params"
      (is (match? 3
                  (d/q '[:find (count ?name) .
                         :where [_ :team/name ?name]] db))))

    ;;
    ;; Single scalar
    ;;

    (testing "high priority tasks"
      (is (match? (m/in-any-order ["Water the plants"
                                   "Dust the shelves"
                                   "Clean the bathroom"])
                  (d/q '[:find [?title ...]
                         :in $ ?priority
                         :where
                         [?task :task/priority ?priority]
                         [?task :task/title ?title]]
                       db    ;; this is $
                       :high ;; this is ?priority
                       ))))

    ;;
    ;; Collection Binding
    ;; https://docs.datomic.com/on-prem/query/query.html#collection-binding
    ;;
    (testing "tasks for teams `Beta` OR `Gamma`"
      (is (match? 6
                  (d/q '[:find (count ?task) .
                         :in $ [?teams ...]
                         :where
                         [?task :task/team ?team]
                         [?team :team/name ?teams]]
                       db               ;; this is $
                       ["Beta" "Gamma"] ;; this is ?teams
                       ))))

    ;;
    ;; Relation Binding
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
                       ;; this is $
                       db
                       ;; this destructured into ?priority and ?completed
                       [[:low false]
                        [:medium true]]))))))

;;
;; Not Clauses
;;
;; not clauses allow you to express that one or more logic variables inside
;; a query must not satisfy all of a set of predicates.
;;

(deftest not-clauses-test
  (let [conn (database/recreate)
        _ @(d/transact conn tasks/schema)
        _ @(d/transact conn tasks/teams)
        _ @(d/transact conn tasks/tasks)
        db (d/db conn)]

    (testing "total unassigned tasks"
      (is (match? 2
                  (d/q '[:find (count ?task) .
                         :where
                         [?task :task/title]
                         (not [?task :task/team])] db))))

    (testing "teams without high priority tasks"
      (is (match? "Gamma"
                  (d/q '[:find ?team-name .
                         :where
                         [?team :team/name ?team-name]
                         (not-join [?team]
                                   [?task :task/team ?team]
                                   [?task :task/priority :high])] db))))))

;;
;; Function Expression
;;

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
;; Pull API
;;

(deftest pull-api-test
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

;;
;; Reverse Lookup
;;

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

;;
;; History API
;;

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

;;
;; Entity API
;;

(deftest entity-api-test
  (let [conn (database/recreate)
        _ @(d/transact conn tasks/schema)
        _ @(d/transact conn tasks/teams)
        _ @(d/transact conn tasks/tasks)
        db (d/db conn)
        beta-team (d/entity db [:team/name "Beta"])
        any-task-id (d/q '[:find ?t . :where [?t :task/title]] db)]

    (testing "beta looks like just a simple map with an id"
      (is (match? {:db/id int?}
                  beta-team)))

    (testing "but it is actually an EntityMap, which is lazy"
      (is (match? datomic.query.EntityMap
                  (class beta-team))))

    (testing "from which we can access specific attributes"
      (is (match? "Sigma"
                  (:team/business-unit beta-team))))

    (testing "or all of them in any way we want"
      (is (match? [:team/name :team/business-unit]
                  (keys beta-team))))

    (testing "like this"
      (is (match? [[:task/title string?]
                   [:task/completed boolean?]
                   [:task/priority keyword?]
                   [:task/team {:db/id int?}]]
                  (seq (d/entity db any-task-id)))))

    (testing "or even this"
      (is (match? {:db/id int?
                   :task/title string?
                   :task/completed boolean?
                   :task/priority keyword?
                   :task/team {:db/id int?}}
                  (d/touch (d/entity db any-task-id)))))

    (testing "and this also works"
      (is (match? (m/in-any-order ["Water the plants"
                                   "Mow the lawn"
                                   "Iron the clothes"
                                   "Do the laundry"])
                  (->> beta-team
                       :task/_team
                       (map #(:task/title (d/entity db (:db/id %))))))))))
;;
;; BYO data
;;

(deftest bring-your-own-data-test
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
