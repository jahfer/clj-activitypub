(ns clj-activitypub.ring.activitypub
  (:require [clojure.data.json :as json]
            [compojure.core :refer [context GET POST context routes]]
            [clj-activitypub.core :as core]))

(defn actor-handler
  [_request actor] 
  {:status 200
   :headers {"Content-Type" "application/jrd+json; charset=utf-8"}
   :body (json/write-str (core/actor actor))})

(defn inbox-handler 
   [_request]
   {:status 202
    :headers {"Content-Type" "application/jrd+json; charset=utf-8"}})

(defn outbox-handler
  ([request]
   (outbox-handler request {}))
  ([request data]
   (let [query-string (when (:query-string request)
                        (str "?" (:query-string request)))]
     {:status 200
      :headers {"Content-Type" "application/jrd+json; charset=utf-8"}
      :body (json/write-str
             (merge {"@context" ["https://www.w3.org/ns/activitystreams"]
                     :id (str "https://" (:server-name request) (:uri request) query-string)
                     :type	"OrderedCollection"
                     :totalItems 0
                     :orderedItems []}
                    data))})))

(defn following-handler
  ([request]
   (following-handler request {}))
  ([request data]
   (let [query-string (when (:query-string request)
                        (str "?" (:query-string request)))]
     {:status 200
      :headers {"Content-Type" "application/jrd+json; charset=utf-8"}
      :body (json/write-str
             (merge {"@context" ["https://www.w3.org/ns/activitystreams"]
                     :id (str "https://" (:server-name request) (:uri request) query-string)
                     :type	"OrderedCollection"
                     :totalItems 0
                     :orderedItems []}
                    data))})))

(defn followers-handler
  ([request]
   (followers-handler request {}))
  ([request data]
   (let [query-string (when (:query-string request)
                        (str "?" (:query-string request)))]
     {:status 200
      :headers {"Content-Type" "application/jrd+json; charset=utf-8"}
      :body (json/write-str
             (merge {"@context" ["https://www.w3.org/ns/activitystreams"]
                     :id (str "https://" (:server-name request) (:uri request) query-string)
                     :type	"OrderedCollection"
                     :totalItems 0
                     :orderedItems []}
                    data))})))

(defn user-routes [domain]
  (context "/users/:username" [username]
    (routes
     (POST "/inbox"    request (inbox-handler request))
     (GET "/outbox"    request (outbox-handler request))
     (GET "/following" request (following-handler request))
     (GET "/followers" request (followers-handler request))
     (GET "/"          request (actor-handler request (core/config {:domain domain
                                                                    :username username}))))))
