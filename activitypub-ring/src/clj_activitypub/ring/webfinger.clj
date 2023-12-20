(ns clj-activitypub.ring.webfinger
  (:require [clojure.string :as str]
            [clojure.data.json :as json]
            [clj-activitypub.webfinger :as webfinger]
            [compojure.core :refer [GET]]
            [ring.middleware.params :refer [wrap-params]]))

(defn handler [{:keys [domain user-route instance-actor-route]
                :or {user-route "/users/"
                     instance-actor-route "/actor"}}
               request]
  (let [base-url (str "https://" domain)
        resource (-> request :query-params (get "resource"))
        username (-> resource
                     (str/replace-first #"acct:" "")
                     webfinger/parse-handle
                     :username)
        body (if (= "instance.actor" username)
               {"subject" resource
                "links" [{"rel" "self"
                          "type" "application/activity+json"
                          "href" (str base-url instance-actor-route)}]}
               ;; else
               {"subject" resource
                "links" [{"rel" "self"
                          "type" "application/activity+json"
                          "href" (str base-url user-route username)}]})]
    {:status 200
     :headers {"Content-Type" "application/jrd+json; charset=utf-8"}
     :body (json/write-str body)}))

(defn routes [routing-config]
  (GET webfinger/remote-uri-path [_]
    (wrap-params (partial handler routing-config))))