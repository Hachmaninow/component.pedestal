(defproject org.h9w/component-pedestal "0.2.0"
  :dependencies [[org.clojure/clojure "1.8.0"]
                 [io.pedestal/pedestal.service "0.5.0"]
                 [io.pedestal/pedestal.jetty "0.5.0"]
                 [com.stuartsierra/component "0.3.1"]]
  :deploy-repositories {"clojars" {
                             :url "https://clojars.org/repo"
                             :sign-releases false
                             }
                 }
  :min-lein-version "2.0.0"
  )
