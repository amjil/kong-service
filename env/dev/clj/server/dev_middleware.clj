(ns server.dev-middleware
  (:require
    [server.config :refer [env]]
    [ring.middleware.reload :refer [wrap-reload]]
    [prone.middleware :refer [wrap-exceptions]]))

(defn wrap-dev [handler]
  (-> handler
      wrap-reload
      ;; disable prone middleware, it can not handle async
      (cond-> (not (env :async?)) (wrap-exceptions {:app-namespaces ['kong-service]}))))
