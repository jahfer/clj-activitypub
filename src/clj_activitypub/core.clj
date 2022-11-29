(ns clj-activitypub.core
  (:require [crypto]
            [thread-cache]
            [http-util :as http]
            [clj-http.client :as client]
            [clojure.string :as str]))

(defn config
  "Creates hash of computed data relevant for most ActivityPub utilities."
  [{:keys [domain username username-route] :or {username-route "/users/"}}]
  (let [base-url (str "https://" domain)]
    {:domain domain
     :base-url base-url
     :username username
     :user-id (str base-url username-route username)
     :private-key crypto/test-private-key}))

(defn parse-account
  "Given an ActivityPub handle (e.g. @jahfer@mastodon.social), produces
   a hash-map containing {:domain ... :username ...}."
  [handle]
  (let [[username domain] (filter #(not (str/blank? %))
                                  (str/split handle #"@"))]
    {:domain domain :username username}))

(def user-cache
  (thread-cache/make))

(defn fetch-user
  "Fetches the customer account details located at user-id
   from a remote server."
  [user-id]
  ((:get-v user-cache)
   user-id
   #(:body
     (client/get user-id {:as :json-string-keys
                          :throw-exceptions false
                          :ignore-unknown-host? true
                          :headers {"Accept" "application/activity+json"}}))))

(defn actor [{:keys [user-id username]}]
  {"@context" ["https://www.w3.org/ns/activitystreams"]
   :id user-id
   :type "Person"
   :preferredUsername username
   :inbox (str user-id "/inbox")
   :publicKey {:id (str user-id "#main-key")
               :owner user-id
               :publicKeyPem crypto/test-public-key-str}})

(def signature-headers ["(request-target)" "host" "date" "digest"])

(defn str-for-signature [headers]
  (let [headers-xf (reduce-kv
                    (fn [m k v]
                      (assoc m (str/lower-case k) v)) {} headers)]
    (->> signature-headers
         (select-keys headers-xf)
         (reduce-kv (fn [coll k v] (conj coll (str k ": " v))) [])
         (interpose "\n")
         (apply str))))

(defn gen-signature-header [{:keys [user-id private-key]} headers]
  (let [string-to-sign (str-for-signature headers)
        signature (crypto/base64-encode (crypto/sign string-to-sign private-key))
        sig-header-keys {"keyId" user-id
                         "headers" (str/join " " signature-headers)
                         "signature" signature}]
    (->> sig-header-keys
         (reduce-kv (fn [m k v]
                      (conj m (str k "=" "\"" v "\""))) [])
         (interpose ",")
         (apply str))))

(defn auth-headers [config {:keys [body headers]}]
  (let [digest (http/digest body)
        h (-> headers
              (assoc "Digest" digest)
              (assoc "(request-target)" "post /inbox"))]
    (assoc headers
           "Signature" (gen-signature-header config h)
           "Digest" digest)))

(defmulti activity (fn [_config activity-type _data] activity-type))

(defmethod activity :create [{:keys [user-id]} _ data]
  {"@context" "https://www.w3.org/ns/activitystreams"
   "type" "Create"
   "actor" user-id
   "object" data})

(defmethod activity :delete [{:keys [user-id]} _ data]
  {"@context" "https://www.w3.org/ns/activitystreams"
   "type" "Delete"
   "actor" user-id
   "to" ["https://www.w3.org/ns/activitystreams#Public"]
   "object" data})

(defmulti obj (fn [_config object-data]
                (:type object-data)))

(defmethod obj :note
  [{:keys [user-id]}
   {:keys [id published inReplyTo content to]
    :or {published (http/date)
         inReplyTo nil
         to "https://www.w3.org/ns/activitystreams#Public"}}]
  {"id" (str user-id "/cards/" id)
   "type" "Note"
   "published" published
   "attributedTo" user-id
   "inReplyTo" inReplyTo
   "content" content
   "to" to})

(defn with-config [config]
  (let [f (juxt
           #(partial activity %)
           #(partial obj %))
        [activity-fn obj-fn] (f config)]
    {:activity activity-fn
     :obj obj-fn}))