(ns server.db.core
  (:require
    [next.jdbc.date-time]
    [next.jdbc.result-set]
    [conman.core :as conman]
    [mount.core :refer [defstate]]
    [server.config :refer [env]]

    [next.jdbc :as jdbc]
    [next.jdbc.sql :as sql]
    [next.jdbc.result-set :as rs]))

(defstate ^:dynamic *db*
          :start (conman/connect! {:jdbc-url (env :database-url)})
          :stop (conman/disconnect! *db*))

(conman/bind-connection *db* "sql/queries.sql")

(extend-protocol next.jdbc.result-set/ReadableColumn
  java.sql.Timestamp
  (read-column-by-label [^java.sql.Timestamp v _]
    (.toLocalDateTime v))
  (read-column-by-index [^java.sql.Timestamp v _2 _3]
    (.toLocalDateTime v))
  java.sql.Date
  (read-column-by-label [^java.sql.Date v _]
    (.toLocalDate v))
  (read-column-by-index [^java.sql.Date v _2 _3]
    (.toLocalDate v))
  java.sql.Time
  (read-column-by-label [^java.sql.Time v _]
    (.toLocalTime v))
  (read-column-by-index [^java.sql.Time v _2 _3]
    (.toLocalTime v)))

;;  --------------------------------------
(defn update! [t w s]
  (sql/update! *db* t w s
    {:builder-fn rs/as-unqualified-lower-maps}))

(defn get-by-id [t id]
  (sql/get-by-id *db* t id
    {:builder-fn rs/as-unqualified-lower-maps}))

(defn find-by-keys
  ([t w]
   (sql/find-by-keys *db* t w
     {:builder-fn rs/as-unqualified-lower-maps}))
  ([t w ex]
   (sql/find-by-keys *db* t w
     (merge
       {:builder-fn rs/as-unqualified-lower-maps}
       ex))))

(defn find-one-by-keys [t w]
  (first
    (sql/find-by-keys *db* t w
      {:builder-fn rs/as-unqualified-lower-maps})))

(defn insert! [t info]
  (sql/insert! *db* t info
    {:builder-fn rs/as-unqualified-lower-maps}))

(defn delete! [t w]
  (sql/delete! *db* t w))

(defn execute!
  ([sqlmap]
   (jdbc/execute! *db* sqlmap
     {:builder-fn rs/as-unqualified-lower-maps}))
  ([sqlmap opt]
   (jdbc/execute! *db* sqlmap
     (merge
       {:builder-fn rs/as-unqualified-lower-maps}
       opt))))
