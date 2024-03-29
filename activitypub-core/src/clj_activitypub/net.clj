(ns clj-activitypub.net
  (:require [clj-activitypub.internal.thread-cache :as tc]
            [clj-activitypub.internal.crypto :as crypto]
            [clj-activitypub.internal.http-util :as http]
            [clj-http.client :as client]
            [clojure.set :refer [union]]
            [clojure.string :as str]
            [clojure.walk :refer [stringify-keys]])
  (:import [java.net URI]))

(def signature-headers ["(request-target)" "host" "date" "digest"])

(defn- normalize-headers [headers]
  (reduce-kv
   (fn [m k v]
     (assoc m (str/lower-case k) v)) {} headers))

(defn- str-for-signature [headers]
  (->> signature-headers
       (select-keys headers)
       (reduce-kv (fn [coll k v] (conj coll (str k ": " v))) [])
       (interpose "\n")
       (apply str)))

(defn gen-signature-header
  "Generates a HTTP Signature string based on the provided map of headers."
  [config headers]
  (let [{:keys [user-id private-key]} config
        normalized-headers (normalize-headers headers)
        string-to-sign (str-for-signature normalized-headers)
        signature (crypto/base64-encode (crypto/sign string-to-sign private-key))
        sig-header-keys {"keyId" (str user-id "#main-key")
                         "algorithm" "rsa-sha256"
                         "headers" (->> signature-headers
                                        (filter #(contains? normalized-headers %))
                                        (str/join " "))
                         "signature" signature}]
    (->> sig-header-keys
         (reduce-kv (fn [m k v]
                      (conj m (str k "=" "\"" v "\""))) [])
         (interpose ",")
         (apply str))))

(defn auth-headers
  "Given a config and request map of {:body ... :headers ... :request-target ...},
   returns the original set of headers with Signature and Digest attributes
   appended. If Date is not in the original header set, it will also be appended."
  [config {:keys [body headers request-target]
           :or {body "" headers {}}}]
  (let [digest (http/digest body)
        headers (cond-> headers
                  (not (contains? headers "Date")) (assoc "Date" (http/date))
                  (seq body) (assoc "Digest" digest))
        headers' (assoc headers "(request-target)" request-target)]
    (assoc headers
           "Signature" (gen-signature-header config headers'))))

(def ^:private object-cache (tc/make))

(defn reset-cache!
  "Removes all entries from the object cache."
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

(defn- authorized? [x]
  (and (map? x)
       (contains? x :authority)))

(defn- authorize [x config]
  {:id x :authority config})

(defn- resolve-collection! [object resolve-fn]
  (let [type (:type object)]
    (condp some (if (coll? type) type [type])
      collection-type?      (lazy-resolve! (or (:first object)
                                               (:orderedItems object)
                                               (:items object)) resolve-fn)
      collection-page-type? (let [items (or (:orderedItems object) (:items object))]
                              (cond-> []
                                items          (concat (map #(lazy-resolve! % resolve-fn) items))
                                (:next object) (concat (ensure-seq (lazy-resolve! (:next object) resolve-fn))))))))

(defn- fetch-objects!
  [remote-id]
  ;; TODO: Don't cache failed request
  (tc/fetch object-cache remote-id
            #(delay (some-> (try (client/get remote-id http/GET-config)
                                 (catch Exception e (println "[fetch-objects!] caught exception: " (.getMessage e))
                                        nil))
                            (:body)
                            (lazy-resolve!)))))

(defn- authorized-fetch-objects!
  [remote-id authority]
  ;; TODO: Don't cache failed request
  (tc/fetch object-cache remote-id
            #(delay (let [uri (URI. remote-id)
                          headers (merge (:headers http/GET-config)
                                         {"Host" (.getHost uri)})
                          signed-headers (auth-headers authority {:headers headers
                                                                  :request-target (str "get " (.getPath uri))})]
                      (some-> (try (client/get remote-id
                                               (assoc-in http/GET-config
                                                         [:headers]
                                                         (dissoc signed-headers "(request-target)")))
                                   (catch Exception e (println "[authorized-fetch-objects!] caught exception: " (.getMessage e))
                                          nil))
                              (:body)
                              (authorize authority)
                              (lazy-resolve!))))))

(defn- lazy-resolve!
  ([str-or-obj]
   (if (authorized? str-or-obj)
     (lazy-resolve! (:id str-or-obj) #(authorized-fetch-objects! % (:authority str-or-obj)))
     (lazy-resolve! str-or-obj fetch-objects!)))

  ([str-or-obj resolve-fn]
   (condp apply [str-or-obj]
     string? (resolve-fn str-or-obj)
     map? (if (any-collection-type? (:type str-or-obj))
            (resolve-collection! str-or-obj resolve-fn)
            str-or-obj)
     sequential? str-or-obj
     delay? str-or-obj
     nil? [])))

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

(defn authorized-resolve!
  "Same as [[resolve!]] but with a signature header added to the request.
   You should expect the remote server to make a call to the actor identified
   in `config` to verify the signing key."
  [str-or-obj config]
  (-> str-or-obj
      (authorize config)
      (resolve!)))

(defn fetch-actor!
  "Fetches the actor located at user-id from a remote server. If you wish to
   retrieve a list of objects, see [[resolve!]]. Will return a cached
   result if it exists in memory."
  [user-id]
  (let [object (first (resolve! user-id))]
    (when (actor-type? (:type object))
      object)))

(defn authorized-fetch-actor!
  "Same as [[fetch-actor!]] but with a signature header added to the request.
   You should expect the remote server to make a call to the actor identified
   in `config` to verify the signing key."
  [user-id config]
  (let [object (first (authorized-resolve! user-id config))]
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