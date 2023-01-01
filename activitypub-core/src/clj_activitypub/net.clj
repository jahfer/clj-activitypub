(ns clj-activitypub.net
  (:require [clj-activitypub.internal.thread-cache :as thread-cache]
            [clj-activitypub.internal.crypto :as crypto]
            [clj-activitypub.internal.http-util :as http]
            [clj-http.client :as client]
            [clojure.set :refer [union]]
            [clojure.string :as str]
            [clojure.walk :refer [stringify-keys]]))

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
   original set of headers with Signature and Digest attributes appended. If
   Date is not in the original header set, it will also be appended."
  [config {:keys [body headers] :or {headers {}}}]
  (let [digest (http/digest body)
        headers (cond-> headers
                  (not (contains? headers "Date")) (assoc "Date" (http/date)))
        headers' (-> headers
                     (assoc "Digest" digest)
                     (assoc "(request-target)" "post /inbox"))]
    (assoc headers
           "Signature" (gen-signature-header config headers')
           "Digest" digest)))

(def ^:private object-cache (thread-cache/make))

(defn reset-object-cache!
  "Removes all entries from the object cache, which is populated with results
   from [[fetch-objects!]] or [[fetch-user!]]."
  []
  ((:reset object-cache)))

(def actor-type? #{"Person" "Service" "Application"})
(def terminal-object-type? (union actor-type?))
(def collection-type? #{"OrderedCollection" "Collection"})
(def collection-page-type? #{"OrderedCollectionPage" "CollectionPage"})
(def any-collection-type? (union collection-type? collection-page-type?))

(declare resolve!)

(defn- resolve-collection! [object]
  (let [type (:type object)]
    (condp some (if (coll? type) type [type])
      actor-type?           [object]
      collection-type?      (resolve! (:first object))
      collection-page-type? (let [items (or (:orderedItems object) (:items object))]
                              (cond-> []
                                items          (concat (map resolve! items))
                                (:next object) (concat (resolve! (:next object))))))))

(defn- fetch-objects!
  [remote-id]
  (let [fetch (:get-v object-cache)]
    (fetch remote-id #(some-> (client/get remote-id http/GET-config)
                              (:body)
                              (resolve!)))))

(defn resolve!
  "Fetches the resource(s) located at remote-id from a remote server. Results
   are returned as a collection. If URL points to an ActivityPub Collection,
   the links will be followed until a resolved object is found. Will return
   cached results if they exist in memory."
  [str-or-obj]
  (condp apply [str-or-obj]
    string? (fetch-objects! str-or-obj)
    map? (if (any-collection-type? (:type str-or-obj))
           (resolve-collection! str-or-obj)
           [str-or-obj])
    list? str-or-obj
    nil? []))

(defn fetch-actor!
  "Fetches the actor located at user-id from a remote server. If you wish to
   retrieve a list of objects, see [[fetch-objects!]]. Will return a cached
   result if it exists in memory."
  [user-id]
  (let [object (first (resolve! user-id))]
    (when (actor-type? (:type object))
      object)))

(defn delivery-targets!
  "Returns the distinct inbox locations for the audience of the activity.
   Following the ActivityPub spec, this includes the :to, :bto, :cc, :bcc, and
   :audience fields while also removing the author's own address. If the user's
   server supports a sharedInbox, that location is returned instead."
  [activity] 
  ;; TODO Look up addressing fields in target, inReplyTo, and tag,
  ;; then retrieve their actor or attributedTo fields
  (let [activity (stringify-keys activity)
        object (get activity "object")
        actor-id (get activity "actor")
        remote-ids (-> object
                       (select-keys ["to" "bto" "cc" "bcc" "audience"])
                       (vals)
                       (flatten)
                       (distinct))]
    (->> remote-ids
         (mapcat fetch-objects!)
         (group-by #(or (get-in % [:endpoints :sharedInbox])
                        (get % :inbox)))
         (keys)
         (distinct)
         (remove #(= % actor-id)))))
