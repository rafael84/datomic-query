(defproject datomic-query "0.1.0-SNAPSHOT"
  :description "Datomic Query"
  :url "https://github.com/rafael84/datomic-query"
  :license {:name "EPL-2.0 OR GPL-2.0-or-later WITH Classpath-exception-2.0"
            :url "https://www.eclipse.org/legal/epl-2.0/"}
  :profiles {:test {}
             :repl {}}
  :dependencies [[org.clojure/clojure "1.11.1"]
                 [com.datomic/datomic-free "0.9.5697"]]
  :repl-options {:init-ns datomic-query.core})
