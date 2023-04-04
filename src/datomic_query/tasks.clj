(ns datomic-query.tasks 
  (:require
   [datomic-query.helper :as helper]))

(def schema
  [;;
   ;; team (name, business-unit)
   ;;
   {:db/ident       :team/name
    :db/unique      :db.unique/identity
    :db/valueType   :db.type/string
    :db/cardinality :db.cardinality/one}
   {:db/ident       :team/business-unit
    :db/valueType   :db.type/string
    :db/cardinality :db.cardinality/one}
   ;;
   ;; task (title, due-date, completed, priority, team)
   ;;
   {:db/ident :task/title     :db/valueType :db.type/string  :db/cardinality :db.cardinality/one}
   {:db/ident :task/due-date  :db/valueType :db.type/instant :db/cardinality :db.cardinality/one}
   {:db/ident :task/completed :db/valueType :db.type/boolean :db/cardinality :db.cardinality/one}
   {:db/ident :task/priority  :db/valueType :db.type/keyword :db/cardinality :db.cardinality/one}
   {:db/ident :task/team      :db/valueType :db.type/ref     :db/cardinality :db.cardinality/one}])

(def teams
  [{:team/name "Alpha" :team/business-unit "Sigma"}
   {:team/name "Beta"  :team/business-unit "Sigma"}
   {:team/name "Gamma" :team/business-unit "Omega"}])

(defn- task
  [title team-name priority completed due-date]
  (-> {:task/title title
       :task/team (when team-name [:team/name team-name])
       :task/priority priority
       :task/due-date due-date
       :task/completed completed}
      helper/remove-nils))

(def tasks
  ;;     title                team-name   priority  completed  due-date
  [(task "Wash the dishes"    "Alpha"     :low      true       nil)
   (task "Take out the trash" "Alpha"     :medium   false      nil)
   (task "Dust the shelves"   "Alpha"     :high     true       nil)
   (task "Do the laundry"     "Beta"      :medium   true       nil)
   (task "Iron the clothes"   "Beta"      :low      false      nil)
   (task "Water the plants"   "Beta"      :high     false      nil)
   (task "Mow the lawn"       "Beta"      :medium   false      #inst "2023-12")
   (task "Clean the windows"  "Gamma"     :medium   false      #inst "2024-01")
   (task "Sweep the floor"    "Gamma"     :low      false      #inst "2023-11-15")
   (task "Vacuum the carpet"  nil         :medium   false      nil)
   (task "Clean the bathroom" nil         :high     true       nil)])
