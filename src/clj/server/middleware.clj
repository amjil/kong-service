(ns server.middleware
  (:require
    [server.env :refer [defaults]]
    [server.config :refer [env]]
    [ring.middleware.defaults :refer [site-defaults wrap-defaults]]))


(defn wrap-base [handler]
  (-> ((:middleware defaults) handler)
      (wrap-defaults
        (-> site-defaults
            (assoc-in [:security :anti-forgery] false)))))
