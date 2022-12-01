(ns clj-activitypub.ring.webfinger
  (:require [clojure.string :as str]
            [clojure.data.json :as json]
            [clj-activitypub.core :as activitypub]
            [compojure.core :refer [GET]]
            [ring.middleware.params :refer [wrap-params]]
            [clj-activitypub.webfinger :as webfinger]))

(defn handler [domain request]
  (let [resource (-> request :query-params (get "resource"))
        username (-> resource
                     (str/replace-first #"acct:" "")
                     activitypub/parse-account
                     :username)]
    {:status 200
     :headers {"Content-Type" "application/jrd+json; charset=utf-8"}
     :body (json/write-str
            {"subject" resource
             "links" [{"rel" "self"
                       "type" "application/activity+json"
                       "href" (str "https://" domain "/users/" username)}]})}))

(defn routes [base-domain]
  (GET webfinger/remote-uri-path [_]
    (wrap-params (partial handler base-domain))))