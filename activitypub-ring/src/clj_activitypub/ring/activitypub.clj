(ns clj-activitypub.ring.activitypub
  (:require [clojure.data.json :as json]
            [clj-activitypub.core :as activitypub]
            [compojure.core :refer [context GET POST context routes]]))

(defn default-actor-handler [actor] 
  {:status 200
   :headers {"Content-Type" "application/jrd+json; charset=utf-8"}
   :body (json/write-str (activitypub/actor actor))})

(defn default-card-handler [_request]
  {:status 200
   :headers {"Content-Type" "application/jrd+json; charset=utf-8"}
   :body "{}"})

(defn default-inbox-handler [_request]
  {:status 202
   :headers {"Content-Type" "application/jrd+json; charset=utf-8"}
   :body ""})

(defn default-outbox-handler [_request]
  {:status 200
   :headers {"Content-Type" "application/jrd+json; charset=utf-8"}
   :body "{}"})

(defn- wrap-current-user [domain username handler]
  (fn [request]
    (if (nil? (:activity-user request))
      (let [user (activitypub/config {:domain domain :username username})]
        (handler (assoc request :activity-user user)))
      (handler request))))

(defn user-routes [domain]
  (context "/users/:username" [username]
    (wrap-current-user domain username
     (routes
      (POST "/inbox"    {:keys [activity-user]} (default-inbox-handler activity-user))
      (GET "/outbox"    {:keys [activity-user]} (default-outbox-handler activity-user))
      (GET "/cards/:id" {:keys [activity-user]} (default-card-handler activity-user))
      (GET "/"          {:keys [activity-user]} (default-actor-handler activity-user))))))
