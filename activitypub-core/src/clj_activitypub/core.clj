(ns clj-activitypub.core
  (:require [clj-activitypub.internal.crypto :as crypto]
            [clj-activitypub.internal.thread-cache :as thread-cache]
            [clj-activitypub.internal.http-util :as http]
            [clj-http.client :as client]
            [clojure.string :as str]))

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

(defn parse-account
  "Given an ActivityPub handle (e.g. @jahfer@mastodon.social), produces
   a map containing {:domain ... :username ...}."
  [handle]
  (let [[username domain] (filter #(not (str/blank? %))
                                  (str/split handle #"@"))]
    {:domain domain :username username}))

(def ^:private user-cache (thread-cache/make))

(defn reset-user-cache
  "Removes all entries from the user cache, which is populated with results
   from [[fetch-users]] or [[fetch-user]]."
  []
  ((:reset user-cache)))

(defn fetch-users
  "Fetches the actor(s) located at user-id from a remote server. Results are
   returned as a collection. If URL points to an ActivityPub Collection, the
   links will be followed until max-depth is reached. Will return cached
   results if they exist in memory."
  ([user-id] (fetch-users user-id 3 0))
  ([user-id max-depth] (fetch-users user-id max-depth 0))
  ([user-id max-depth current-depth]
   (when (< current-depth max-depth)
     ((:get-v user-cache)
      user-id
      (fn []
        (when-let [response (client/get user-id {:as :json
                                                 :throw-exceptions false
                                                 :ignore-unknown-host? true
                                                 :headers {"Accept" "application/activity+json"}})]
          (let [body (:body response)
                depth' (inc current-depth)] 
            (condp = (:type body)
              "OrderedCollection" (concat (fetch-users (:first body) depth'))
              "Collection" (concat (fetch-users (:first body) depth'))
              "OrderedCollectionPage" (concat (mapcat #(fetch-users % depth') (:orderedItems body))
                                              (if (:next body)
                                                (fetch-users (:next body) current-depth)
                                                []))
              "CollectionPage" (concat (mapcat #(fetch-users % depth') (:items body))
                                       (if (:next body)
                                         (fetch-users (:next body) current-depth)
                                         []))
              "Person" [body]
              (println (str "Unknown response for ID " user-id))))))))))

(defn fetch-user
  "Fetches the actor located at user-id from a remote server. Links to remote
   ActivityPub Collections will not be followed. If you wish to retrieve a list
   of users, see [[fetch-users]]. Will return a cached result if it exists in
   memory."
  [user-id]
  (first (fetch-users user-id 1)))

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

(def signature-headers ["(request-target)" "host" "date" "digest"])

(defn- str-for-signature [headers]
  (let [headers-xf (reduce-kv
                    (fn [m k v]
                      (assoc m (str/lower-case k) v)) {} headers)]
    (->> signature-headers
         (select-keys headers-xf)
         (reduce-kv (fn [coll k v] (conj coll (str k ": " v))) [])
         (interpose "\n")
         (apply str))))

(defn gen-signature-header
  "Generates a HTTP Signature string based on the provided map of headers."
  [config headers]
  (let [{:keys [user-id private-key]} config
        string-to-sign (str-for-signature headers)
        signature (crypto/base64-encode (crypto/sign string-to-sign private-key))
        sig-header-keys {"keyId" user-id
                         "headers" (str/join " " signature-headers)
                         "signature" signature}]
    (->> sig-header-keys
         (reduce-kv (fn [m k v]
                      (conj m (str k "=" "\"" v "\""))) [])
         (interpose ",")
         (apply str))))

(defn auth-headers
  "Given a config and request map of {:body ... :headers ...}, returns the
   original set of headers with Signature and Digest attributes appended."
  [config {:keys [body headers]}]
  (let [digest (http/digest body)
        h (-> headers
              (assoc "Digest" digest)
              (assoc "(request-target)" "post /inbox"))]
    (assoc headers
           "Signature" (gen-signature-header config h)
           "Digest" digest)))

(defmulti obj
  "Produces a map representing an ActivityPub object which can be serialized
   directly to JSON in the form expected by the ActivityStreams 2.0 spec.
   See https://www.w3.org/TR/activitystreams-vocabulary/ for reference."
  (fn [_config object-data] (:type object-data)))

(defmethod obj :note
  [{:keys [user-id]}
   {:keys [id published inReplyTo content to]
    :or {published (http/date)
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