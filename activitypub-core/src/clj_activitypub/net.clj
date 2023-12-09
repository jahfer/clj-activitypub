(ns clj-activitypub.net
  (:require [clj-activitypub.internal.thread-cache :as tc]
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

(def ^:private object-cache (tc/make))

(defn reset-object-cache!
  "Removes all entries from the object cache, which is populated with results
   from [[fetch-objects!]] or [[fetch-user!]]."
  []
  (tc/reset object-cache))

(def actor-type? #{"Person" "Service" "Application"})
(def terminal-object-type? (union actor-type?))
(def collection-type? #{"OrderedCollection" "Collection"})
(def collection-page-type? #{"OrderedCollectionPage" "CollectionPage"})
(def any-collection-type? (union collection-type? collection-page-type?))


(declare resolve!)
(declare lazy-resolve!)

(defn- ensure-seq [x]
  (if (sequential? x) x [x]))

(defn- resolve-collection! [object]
  (let [type (:type object)]
    (condp some (if (coll? type) type [type])
      collection-type?      (lazy-resolve! (:first object))
      collection-page-type? (let [items (or (:orderedItems object) (:items object))]
                              (cond-> []
                                items          (concat (map lazy-resolve! items))
                                (:next object) (concat (ensure-seq (lazy-resolve! (:next object)))))))))

(defn- fetch-objects!
  [remote-id]
  (tc/fetch object-cache remote-id
            #(delay (do
                      ;; (println "Performing GET" remote-id)
                      (some-> (try (client/get remote-id http/GET-config)
                                   (catch Exception _ nil))
                              (:body)
                              (lazy-resolve!))))))

(defn lazy-resolve!
  [str-or-obj]
  (condp apply [str-or-obj]
    string? (fetch-objects! str-or-obj)
    map? (if (any-collection-type? (:type str-or-obj))
           (resolve-collection! str-or-obj)
           str-or-obj)
    sequential? str-or-obj
    delay? str-or-obj
    nil? []))

(defn resolve!
  "Fetches the resource(s) located at remote-id from a remote server. Results
   are returned as a collection. If URL points to an ActivityPub Collection,
   the links will be followed until a resolved object is found. Will return
   cached results if they exist in memory."
  [str-or-obj]
  (let [result (-> str-or-obj
                   (lazy-resolve!)
                   (ensure-seq))]
    (letfn [(branch? [x] (or (delay? x)
                             (sequential? x)))
            (children [x] (map force (ensure-seq x)))]
      (remove branch? (tree-seq branch? children result)))))

;; (def coll [:a :b (delay [:c :d])])
;; (remove #(-> % force coll?) (tree-seq #(-> % force coll?) #(-> % force seq) coll))

(defn fetch-actor!
  "Fetches the actor located at user-id from a remote server. If you wish to
   retrieve a list of objects, see [[fetch-objects!]]. Will return a cached
   result if it exists in memory."
  [user-id]
  (let [object (first (resolve! user-id))]
    (when (actor-type? (:type object))
      object)))

(defn delivery-targets!
  "Returns the distinct inbox locations for the audience of the activity. This
   includes the :to, :cc, :audience, :target, :inReplyTo, :object, and :tag
   fields while also removing the author's own address. If the user's server
   supports a sharedInbox, that location is returned instead."
  [activity]
  (let [activity (stringify-keys activity)]
    (->> (map activity ["to" "cc" "audience" "target"
                        "inReplyTo" "object" "tag"])
         (flatten)
         (map #(condp apply [%]
                 map? (or (get % "actor")
                          (get % "attributedTo")
                          (when (actor-type? (get % "type"))
                            (get % "id")))
                 string? %
                 nil))
         (remove nil?)
         (distinct)
         (remove #(= % (get activity "actor")))
         (mapcat resolve!)
         (map #(or (get-in % [:endpoints :sharedInbox])
                   (get % :inbox)))
         (distinct)
         (remove nil?))))