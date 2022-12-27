(ns clj-activitypub.core
  (:require [clj-activitypub.internal.crypto :as crypto])
  (:import (java.time OffsetDateTime ZoneOffset)
           (java.time.format DateTimeFormatter)))

(defn date []
  (-> (OffsetDateTime/now (ZoneOffset/UTC))
      (.format DateTimeFormatter/ISO_INSTANT)))

(defn config
  "Creates hash of computed data relevant for most ActivityPub utilities."
  [{:keys [domain username username-route public-key private-key]
    :or {username-route "/users/"
         public-key nil
         private-key nil}}]
  (let [base-url (str "https://" domain)]
    {:domain domain
     :base-url base-url
     :username username
     :user-id (str base-url username-route username)
     :public-key public-key
     :private-key (when private-key
                    (crypto/private-key private-key))}))

(defn actor
  "Accepts a config, and returns a map in the form expected by the ActivityPub
   spec. See https://www.w3.org/TR/activitypub/#actor-objects for reference."
  [{:keys [user-id username public-key]}]
  {"@context" ["https://www.w3.org/ns/activitystreams"
               "https://w3id.org/security/v1"]
   :id user-id
   :type "Person"
   :preferredUsername username
   :inbox (str user-id "/inbox")
   :outbox (str user-id "/outbox")
   :publicKey {:id (str user-id "#main-key")
               :owner user-id
               :publicKeyPem (or public-key "")}})

(defmulti obj
  "Produces a map representing an ActivityPub object which can be serialized
   directly to JSON in the form expected by the ActivityStreams 2.0 spec.
   See https://www.w3.org/TR/activitystreams-vocabulary/ for reference."
  (fn [_config object-data] (:type object-data)))

(defmethod obj :note
  [{:keys [user-id]}
   {:keys [id published inReplyTo content to]
    :or {published (date)
         inReplyTo ""
         to "https://www.w3.org/ns/activitystreams#Public"}}]
  {"id" (str user-id "/notes/" id)
   "type" "Note"
   "published" published
   "attributedTo" user-id
   "inReplyTo" inReplyTo
   "content" content
   "to" to})

(defmulti activity
  "Produces a map representing an ActivityPub activity which can be serialized
   directly to JSON in the form expected by the ActivityStreams 2.0 spec.
   See https://www.w3.org/TR/activitystreams-vocabulary/ for reference."
  (fn [_config activity-type _data] activity-type))

(defmethod activity :create [{:keys [user-id]} _ data]
  {"@context" ["https://www.w3.org/ns/activitystreams"
               "https://w3id.org/security/v1"]
   "type" "Create"
   "actor" user-id
   "object" data})

(defmethod activity :delete [{:keys [user-id]} _ data]
  {"@context" ["https://www.w3.org/ns/activitystreams"
               "https://w3id.org/security/v1"]
   "type" "Delete"
   "actor" user-id
   "object" data})

(defn with-config
  "Returns curried forms of the #activity and #obj multimethods in the form
   {:activity ... :obj ...}, with the initial parameter set to config."
  [config]
  (let [f (juxt
           #(partial activity %)
           #(partial obj %))
        [activity-fn obj-fn] (f config)]
    {:activity activity-fn
     :obj obj-fn}))