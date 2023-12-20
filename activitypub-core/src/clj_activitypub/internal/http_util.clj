(ns ^:no-doc clj-activitypub.internal.http-util
  (:require [clj-activitypub.internal.crypto :as crypto])
  (:import (java.net URLEncoder)
           (java.time OffsetDateTime ZoneOffset)
           (java.time.format DateTimeFormatter)))

(def GET-config
  {:as :json
   :throw-exceptions true
   :ignore-unknown-host? false
   :headers {"User-Agent" "clj-activitypub"
             "Accept" "application/activity+json, application/ld+json; profile=\"https://www.w3.org/ns/activitystreams\""}})

(defn encode-url-params [params]
  (->> params
       (reduce-kv
        (fn [coll k v]
          (conj coll
                (str (URLEncoder/encode (name k)) "=" (URLEncoder/encode (str v)))))
        [])
       (interpose "&")
       (apply str)))

(defn date []
  (-> (OffsetDateTime/now (ZoneOffset/UTC))
      (.format DateTimeFormatter/RFC_1123_DATE_TIME)))

(defn digest
  "Accepts body from HTTP request and generates string
   for use in HTTP `Digest` request header."
  [body]
  (str "sha-256=" (crypto/sha256-base64 body)))