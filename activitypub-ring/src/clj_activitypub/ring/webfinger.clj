(ns clj-activitypub.ring.webfinger
  (:require [clojure.string :as str]
            [clojure.data.json :as json]
            [clj-activitypub.webfinger :as webfinger]
            [compojure.core :refer [GET]]
            [ring.middleware.params :refer [wrap-params]]))

(defn handler [{:keys [domain user-route]
                :or {user-route "/users/"}}
               request]
  (let [base-url (str "https://" domain)
        resource (-> request :query-params (get "resource"))
        username (-> resource
                     (str/replace-first #"acct:" "")
                     webfinger/parse-handle
                     :username)]
    {:status 200
     :headers {"Content-Type" "application/jrd+json; charset=utf-8"}
     :body (json/write-str
            {"subject" resource
             "links" [{"rel" "self"
                       "type" "application/activity+json"
                       "href" (str base-url user-route username)}]})}))

(defn routes [routing-config]
  (GET webfinger/remote-uri-path [_]
    (wrap-params (partial handler routing-config))))