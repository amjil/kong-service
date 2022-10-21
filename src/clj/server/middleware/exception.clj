(ns server.middleware.exception
  (:require [clojure.tools.logging :as log]
            [expound.alpha :as expound]
            [reitit.coercion :as coercion]
            [reitit.ring.middleware.exception :as exception]))

(derive ::error ::exception)
(derive ::failure ::exception)
(derive ::horror ::exception)

(defn handler [message exception request]
  {:status 500
   :body {:message message
          :exception (.getClass exception)
          :data (ex-data exception)
          :uri (:uri request)}})

(defn- custom-handler [exception request]
  {:status 400
   :body {:msg (-> exception ex-data :msg)}})

(defn- not-found-handler [exception request]
  {:status 404
   :body {:msg (-> exception ex-data :msg)}})

(def exception-middleware
  (exception/create-exception-middleware
   (merge
    exception/default-handlers
    {;; log stack-traces for all exceptions
     ::error (partial handler "error")

     ;; ex-data with ::exception or ::failure
     ::exception (partial handler "exception")

     ;; ex-data with ::exception or ::check
     ::exception/check custom-handler

     ::not-found not-found-handler

     ;; SQLException and all it's child classes
     java.sql.SQLException (partial handler "sql-exception")

     ;; override the default handler
     ::exception/default (partial handler "default")

     ;; log stack-traces for all exceptions
     ::exception/wrap (fn [handler e request]
                        (log/error "xxxxxxx!!")
                        (log/error e (.getMessage e))
                        (log/error (-> e ex-data))
                        (handler e request))})))

(defn ex-throw [info]
  (throw (ex-info "check" {:type ::exception/check
                           :msg  info})))
