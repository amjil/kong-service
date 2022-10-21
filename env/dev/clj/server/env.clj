(ns server.env
  (:require
    [clojure.tools.logging :as log]
    [server.dev-middleware :refer [wrap-dev]]))

(def defaults
  {:init
   (fn []
     (log/info "\n-=[kong-service started successfully using the development profile]=-"))
   :stop
   (fn []
     (log/info "\n-=[kong-service has shut down successfully]=-"))
   :middleware wrap-dev})
