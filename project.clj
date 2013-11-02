(defproject popco-x "0.0.0-SNAPSHOT"
  :url "https://github.com/mars0i/popco-x"
  :license {:name "to be filled in"
            :url "to be filled in"}
  :dependencies [[org.clojure/clojure "1.5.1"]]
  :main popco.core.popco ; this is what lein run will look for -main in
  :profiles {:dev {:dependencies [;[criterium/criterium "0.4.2"] ; benchmarking
                                  [org.clojure/tools.macro "0.1.5"]
                                  [net.mikera/core.matrix "0.13.1"]
                                  [net.mikera/vectorz-clj "0.14.0"]]
                   ;:source-paths ["src/popco/core" "src/popco/sims" "src/utils"] ; where load will look for source files
                   :source-paths ["src"] ; where load will look for source files
                   }})