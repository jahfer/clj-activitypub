(ns clj-activitypub.core
  (:require [clj-activitypub.internal.crypto :as crypto])
  (:import (java.time OffsetDateTime ZoneOffset)
           (java.time.format DateTimeFormatter)))

(defn date []
  (-> (OffsetDateTime/now (ZoneOffset/UTC))
      (.format DateTimeFormatter/ISO_INSTANT)))

(defn config
  "Creates hash of computed data relevant for most ActivityPub utilities."
  [{:keys [domain username name user-route public-key private-key]
    :or {user-route "/users/"
         public-key nil
         private-key nil}}]
  (let [base-url (str "https://" domain)]
    {:domain domain
     :base-url base-url
     :username username
     :name (or name username)
     :user-id (str base-url user-route username)
     :public-key public-key
     :private-key (when private-key
                    (crypto/private-key private-key))}))

(defn actor
  "Accepts a config, and returns a map in the form expected by the ActivityPub
   spec. See https://www.w3.org/TR/activitypub/#actor-objects for reference."
  [{:keys [user-id username name public-key]}]
  {"@context" ["https://www.w3.org/ns/activitystreams"
               "https://w3id.org/security/v1"]
  "id" user-id
  "type" "Person"
  "name" name
  "preferredUsername" username
  "inbox" (str user-id "/inbox")
  "outbox" (str user-id "/outbox")
  "following" (str user-id "/following")
  "followers" (str user-id "/followers")
  "publicKey" {"id" (str user-id "#main-key")
               "owner" user-id
               "publicKeyPem" (or public-key "")}})

(defmulti obj
  "Produces a map representing an ActivityPub object which can be serialized
   directly to JSON in the form expected by the ActivityStreams 2.0 spec.
   See https://www.w3.org/TR/activitystreams-vocabulary/ for reference."
  (fn [_config object-data] (:type object-data)))

(defmethod obj :note
  [{:keys [user-id]}
   {:keys [id published inReplyTo replies content to cc]
    :or {published (date)
         inReplyTo ""
         replies []
         to "https://www.w3.org/ns/activitystreams#Public"
         cc []}}]
  {"id" (str user-id "/notes/" id)
   "type" "Note"
   "published" published
   "attributedTo" user-id
   "inReplyTo" inReplyTo
   "replies" replies
   "content" content
   "to" to
   "cc" cc})

(defmulti activity
  "Produces a map representing an ActivityPub activity which can be serialized
   directly to JSON in the form expected by the ActivityStreams 2.0 spec.
   See https://www.w3.org/TR/activitystreams-vocabulary/ for reference."
  (fn [_config activity-type _data] activity-type))

;; Todo: make "object" a normal field
(defmethod activity :create [{:keys [user-id]} _ data]
  {"@context" ["https://www.w3.org/ns/activitystreams"
               "https://w3id.org/security/v1"]
   "type" "Create"
   "actor" user-id
   "published" (get data "published")
   "to" (get data "to")
   "cc" (get data "cc")
   "object" (get data "object"
                 data)})

(defmethod activity :delete [{:keys [user-id]} _ data]
  {"@context" ["https://www.w3.org/ns/activitystreams"
               "https://w3id.org/security/v1"]
   "type" "Delete"
   "actor" user-id
   "to" (get data "to")
   "cc" (get data "cc")
   "object" (get data "object"
                 data)})

(defmethod activity :follow [{:keys [user-id]} _ remote-user]
  {"@context" ["https://www.w3.org/ns/activitystreams"
               "https://w3id.org/security/v1"]
   "id" (str user-id "/follow/" (java.util.UUID/randomUUID))
   "type" "Follow"
   "actor" user-id
   "to" remote-user
   "object" remote-user})

(defmethod activity :accept [{:keys [user-id]} _ data]
  {"@context" ["https://www.w3.org/ns/activitystreams"
               "https://w3id.org/security/v1"]
   "type" "Accept"
   "actor" user-id
   "to" (get data "actor")
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