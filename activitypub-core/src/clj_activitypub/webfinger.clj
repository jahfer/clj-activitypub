(ns clj-activitypub.webfinger
  (:require [clj-http.client :as client]
            [clj-activitypub.internal.http-util :as http]
            [clj-activitypub.internal.thread-cache :as thread-cache]))

(def remote-uri-path "/.well-known/webfinger")

(defn- resource-str [domain username]
  (str "acct:" username "@" domain))

(defn resource-url
  "Builds a URL pointing to the user's account on the remote server."
  [domain username & [params]]
  (let [resource (resource-str domain username)
        query-str (http/encode-url-params (merge params {:resource resource}))]
    (str "https://" domain remote-uri-path "?" query-str)))

(def ^:private user-id-cache
  (thread-cache/make))

(defn fetch-user-id
  "Follows the webfinger request to a remote domain, retrieving the ID of the requested
   account. Typically returns a string in the form of a URL."
  [domain username]
  ((:get-v user-id-cache)
   (str domain "@" username) ;; cache key
   (fn []
     (let [response (some-> (resource-url domain username {:rel "self"})
                            (client/get {:as :json :throw-exceptions false :ignore-unknown-host? true}))]
       (some->> response :body :links
                (some #(when (= (:type %) "application/activity+json") %))
                :href)))))
