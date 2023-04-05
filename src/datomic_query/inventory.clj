(ns datomic-query.inventory)

(def schema
  [{:db/ident :inventory/sku
    :db/valueType :db.type/string
    :db/unique :db.unique/identity
    :db/cardinality :db.cardinality/one}

   {:db/ident :inventory/type
    :db/valueType :db.type/ref
    :db/cardinality :db.cardinality/one}

   {:db/ident :inventory/colors
    :db/valueType :db.type/ref
    :db/cardinality :db.cardinality/many}

   {:db/ident :inventory/price
    :db/valueType :db.type/long
    :db/cardinality :db.cardinality/one}

   {:db/ident :black}
   {:db/ident :white}
   {:db/ident :purple}

   {:db/ident :shirt}
   {:db/ident :dress}
   {:db/ident :pants}])

(defn- inv
  [sku colors type price]
  {:inventory/sku sku
   :inventory/type type
   :inventory/colors colors
   :inventory/price price})

(def inventory
  [(inv "black-shirt" #{:black} :shirt 10)
   (inv "white-dress" #{:white} :dress 50)
   (inv "white-pants" #{:white} :pants 30)
   (inv "purple-white-shirt" #{:purple :white} :shirt 25)])
