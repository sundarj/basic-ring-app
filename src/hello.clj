(ns hello                                        
  (:require [clojure.data.json :as json]
            [io.pedestal.http :as http]          
            [io.pedestal.http.route :as route]
            [io.pedestal.http.content-negotiation :as contneg]))

(defn ok [body]
  {:status 200
   :body body
   :headers {"Content-Type" "text/html"}})

(defn bad-request []
  {:status 400 :body "Bad request\n"})

(defn not-found []
  {:status 404 :body "Not found\n"})

(defn greeting-for [name]
  (if-not (empty? name)
    (str "Hello, " name "\n")))

(def unmentionables #{"YHWH" "Voldemort" "Mxyzptlk" "Rumplestiltskin" "曹操"})

(defn respond-hello [request]
  (let [name     (get-in request [:query-params :name])
        greeting (greeting-for name)
        resp     (cond
                   (unmentionables name) (not-found)
                   greeting              (ok greeting)
                   :else                 (bad-request))]
    resp))
    
(def echo
  {:name ::echo
   :enter (fn [context]
           (let [request  (:request context)
                 response (ok request)]
             (assoc context :response response)))})
             
(def supported-types ["text/html" "application/edn" "application/json" "text/plain"])

(def negotiate-content (contneg/negotiate-content supported-types))

(def coerce-body
  {:name ::coerce-body
   :leave (fn [context]
            (let [accepted        (get-in context [:request :accept :field] "text/plain")
                  response        (:response context)
                  body            (:body response)
                  coerced-body    (case accepted
                                    "text/html"        body
                                    "text/plain"       body
                                    "application/edn"  (pr-str body)
                                    "application/json" (json/write-str body))
                 updated-response (assoc response
                                         :headers {"Content-Type" accepted}
                                         :body coerced-body)]
              (assoc context :response updated-response)))})

(def routes
  (route/expand-routes
    #{["/greet" :get [coerce-body negotiate-content respond-hello] :route-name :greet]
      ["/echo" :get echo]}))

(defn create-server []
  (http/create-server
    {::http/routes routes
     ::http/type   :jetty
     ::http/port   8890}))

(defn start []
  (http/start (create-server)))
