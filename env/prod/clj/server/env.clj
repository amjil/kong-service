(ns server.env
  (:require [clojure.tools.logging :as log]))

(def defaults
  {:init
   (fn []
     (log/info "\n-=[kong-service started successfully]=-"))
   :stop
   (fn []
     (log/info "\n-=[kong-service has shut down successfully]=-"))
   :middleware identity})
