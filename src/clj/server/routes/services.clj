(ns server.routes.services
  (:require
    [reitit.swagger :as swagger]
    [reitit.swagger-ui :as swagger-ui]
    [reitit.ring.coercion :as coercion]
    [reitit.coercion.spec :as spec-coercion]
    [reitit.ring.middleware.muuntaja :as muuntaja]
    [reitit.ring.middleware.multipart :as multipart]
    [reitit.ring.middleware.parameters :as parameters]
    [server.middleware.exception :as exception]
    [server.middleware.formats :as formats]
    [server.config :refer [env]]
    [spec-tools.data-spec :as ds]
    [ring.util.http-response :refer :all]
    [clojure.tools.logging :as log]
    [clj-http.client :as client]
    [cheshire.core :as cheshire]
    [server.db.core :as db]
    [java-time :as time]))

(defn service-routes []
  ["/api"
   {:coercion spec-coercion/coercion
    :muuntaja formats/instance
    :swagger {:id ::api}
    :middleware [;; query-params & form-params
                 parameters/parameters-middleware
                 ;; content-negotiation
                 muuntaja/format-negotiate-middleware
                 ;; encoding response body
                 muuntaja/format-response-middleware
                 ;; exception handling
                 exception/exception-middleware
                 ; coercion/coerce-exceptions-middleware
                 ;; decoding request body
                 muuntaja/format-request-middleware
                 ;; coercing response bodys
                 coercion/coerce-response-middleware
                 ;; coercing request parameters
                 coercion/coerce-request-middleware
                 ;; multipart
                 multipart/multipart-middleware]}

   ;; swagger documentation
   ["" {:no-doc true
        :swagger {:info {:title "my-api"
                         :description "https://cljdoc.org/d/metosin/reitit"}}}

    ["/swagger.json"
     {:get (swagger/create-swagger-handler)}]

    ["/api-docs/*"
     {:get (swagger-ui/create-swagger-ui-handler
             {:url "/api/swagger.json"
              :config {:validator-url nil}})}]]

   ["/ping"
    {:get (constantly (ok {:message "pong"}))}]

   ;;
   ["/http"
    {:swagger {:tags ["log"]}}
    ["/log"
     {:post {:summary "http log"
             :parameters {:body any?}
             :responses {200 {:body any?}}
             :handler (fn [{{body :body} :parameters}]
                        (log/warn (cheshire/generate-string body))
                        (let [consumer-info     (db/find-one-by-keys :consumer_info {:consumer_id (get-in body [:consumer :username])})
                              route-info        (db/find-one-by-keys :route_info {:route_name (get-in body [:route :name])})


                              uuid              (str (java.util.UUID/randomUUID))

                              url               (get-in body [:request :url])
                              time-length       (->> body :latencies (vals) (apply +))
                              status            (get-in body [:response :status])
                              req-id            uuid
                              remote-ip         (:client_ip body)
                              method            (get-in body [:request :method])
                              message           ""
                              log-time          (str (time/local-date-time))
                              log-id            (clojure.string/replace uuid #"-" "")
                              grant-id          -1
                              from-user-name    (:consumer_name consumer-info)
                              from-user-id      (:consumer_id consumer-info)
                              from-org-name     (:org_name consumer-info)
                              from-org-id       (:org_id consumer-info)
                              from-account-name (:consumer_name consumer-info)
                              from-access-key   ""
                              access-key-id     0
                              count             0
                              asset-type        "table"
                              asset-name        (first (remove clojure.string/blank? [(:asset_name route-info) "[未知资源]"]))
                              asset-id          (first (remove clojure.string/blank? [(:asset_id route-info) "-1"]))

                              response          (client/put
                                                 (str (:es-url env) "/" log-id)
                                                 {:form-params {:url             url
                                                                :timeLength      time-length
                                                                :status          status
                                                                :reqId           req-id
                                                                :remoteIp        remote-ip
                                                                :method          method
                                                                :message         message
                                                                :logTime         log-time
                                                                :logId           log-id
                                                                :grantId         grant-id
                                                                :fromUserName    from-user-name
                                                                :fromUserId      from-user-id
                                                                :fromOrgName     from-org-name
                                                                :fromOrgId       from-org-id
                                                                :fromAccountName from-account-name
                                                                :fromAccessKey   from-access-key
                                                                :accessKeyId     access-key-id
                                                                :count           count
                                                                :assetType       asset-type
                                                                :assetName       asset-name
                                                                :assetId         asset-id}})]

                          (log/warn "create es log response = " response))
                        {:status 200
                         :body {:success true}})}}]]

   ["/routes"
    {:swagger {:tags ["routes"]}
     :post    {:summary    "create new route"
               :parameters {:body {:service_id string?
                                   :route_name string?
                                   :paths      [string?]
                                   :groups     [string?]
                                   :asset_id      string?
                                   :asset_name    string?}}
               :responses  {200 {:body {:code int?
                                        :msg  string?}}}
               :handler    (fn [{{body :body} :parameters}]
                             (log/warn "params = " body)
                             (db/insert! :route_info (select-keys body [:route_name :asset_id :asset_name]))

                             (let [response (client/post
                                             (str (:kong-url env) "/services/"
                                                  (:service_id body)
                                                  "/routes")
                                             {:form-params {:name  (:route_name body)
                                                            :paths (:paths body)}})]
                               (log/warn "create route response = " response))

                             (let [response (client/post
                                             (str (:kong-url env) "/routes/"
                                                  (:route_name body)
                                                  "/plugins")
                                             {:form-params {:name   "acl"
                                                            :config {:allow              (:groups body)
                                                                     :hide_groups_header true}}
                                              :content-type :json})]
                               (log/warn "create route plugin response = " response))
                             (ok {:code 0
                                  :msg  "success"}))}}]

   ["/routes/:id"
    {:swagger {:tags ["routes"]}
     :put     {:summary    "update a route"
               :parameters {:path {:id string?}
                            :body {(ds/opt :paths)    [string?]
                                   (ds/opt :groups)    [string?]
                                   (ds/opt :org_id)        string?
                                   (ds/opt :org_name)      string?}}
               :responses  {200 {:body {:code int?
                                        :msg  string?}}}
               :handler    (fn [{{body     :body
                                  {id :id} :path} :parameters}]
                             (db/update! :route_info
                               {:route_name id}
                               (select-keys body [:asset_id :asset_name]))

                             (let [response (client/put
                                             (str (:kong-url env) "/routes/" id)
                                             {:form-params {:paths (:paths body)}})]
                               (log/warn "update route response = " response))

                             (let [response (client/post
                                             (str (:kong-url env) "/routes/"
                                                  id
                                                  "/plugins")
                                             {:form-params {:name   "acl"
                                                            :config {:allow              (:groups body)
                                                                     :hide_groups_header true}}})]
                               (log/warn "update route plugin response = " response))

                             (ok {:code 0
                                  :msg  "success"}))}

     :delete  {:summary    "delete a route"
               :parameters {:path {:id string?}}
               :responses  {200 {:body {:code int?
                                        :msg  string?}}}
               :handler    (fn [{{body     :body
                                  {id :id} :path} :parameters}]
                             (db/delete! :route_info {:route_name id})

                             (let [response (client/delete
                                             (str (:kong-url env) "/routes/"
                                                  id))]
                               (log/warn "delete route response = " response))
                             (ok {:code 0
                                  :msg  "success"}))}}]


   ["/consumers"
    {:swagger {:tags ["consumers"]}
     :post    {:summary    "create new consumer"
               :parameters {:body {:consumer_id   string?
                                   :consumer_name string?
                                   :group_name    string?
                                   :org_id        string?
                                   :org_name      string?}}
               :responses  {200 {:body {:code int?
                                        :msg  string?}}}
               :handler    (fn [{{body :body} :parameters}]
                             (db/insert! :consumer_info body)

                             (let [response (client/post
                                             (str (:kong-url env) "/consumers")
                                             {:form-params {:username (:consumer_id body)}})]
                               (log/warn "create consumer response = " response))

                             (let [response (client/post
                                             (str (:kong-url env) "/consumers/"
                                                  (:consumer_id body) "/acls")
                                             {:form-params {:group (:group_name body)}})])

                             (let [response (client/post
                                             (str (:kong-url env) "/consumers/"
                                                  (:consumer_id body) "/hmac-auth")
                                             {:form-params {:username (:consumer_id body)
                                                            :secret   "hmac_secret"}})]

                               (log/warn "create consumer hmac response = " response))
                             {:status 200
                              :body   {:code 0
                                       :msg  "success"}})}}]
   ["/consumers/:id"
    {:swagger {:tags ["consumers"]}
     :put     {:summary    "update a consumer"
               :parameters {:path {:id string?}
                            :body {(ds/opt :consumer_name) string?
                                   (ds/opt :group_name)    string?
                                   (ds/opt :org_id)        string?
                                   (ds/opt :org_name)      string?}}
               :responses  {200 {:body {:code int?
                                        :msg  string?}}}
               :handler    (fn [{{body     :body
                                  {id :id} :path} :parameters}]
                             (let [data (db/find-one-by-keys :consumer_info {:consumer_id id})]
                               (if (and (not-empty (:group_name body)) (not= (:group_name data) (:group_name)))
                                 (let [response (client/post
                                                 (str (:kong-url env) "/consumers/"
                                                      id "/acls")
                                                 {:form-params {:group (:group_name body)}})])))
                             (db/update! :consumer_info {:consumer_id id} body)

                             (ok {:code 0
                                  :msg  "success"}))}

     :delete  {:summary    "delete a consumer"
               :parameters {:path {:id string?}}
               :responses  {200 {:body {:code int?
                                        :msg  string?}}}
               :handler    (fn [{{body     :body
                                  {id :id} :path} :parameters
                                 token                        :identity}]
                             (let [response (client/delete
                                             (str (:kong-url env) "/consumers/" id))]
                               (log/warn "create consumer response = " response))

                             (db/delete! :consumer_info {:consumer_id id})

                             (ok {:code 0
                                  :msg  "success"}))}}]



   ["/math"
    {:swagger {:tags ["math"]}}

    ["/plus"
     {:get {:summary "plus with spec query parameters"
            :parameters {:query {:x int?, :y int?}}
            :responses {200 {:body {:total pos-int?}}}
            :handler (fn [{{{:keys [x y]} :query} :parameters}]
                       {:status 200
                        :body {:total (+ x y)}})}
      :post {:summary "plus with spec body parameters"
             :parameters {:body {:x int?, :y int?}}
             :responses {200 {:body {:total pos-int?}}}
             :handler (fn [{{{:keys [x y]} :body} :parameters}]
                        {:status 200
                         :body {:total (+ x y)}})}}]]])
