(ns datomic-query.helper)

(defn remove-nils
  "Removes attributes with `nil` value from the map `m`"
  [m]
  (into {} (remove (comp nil? second) m)))
