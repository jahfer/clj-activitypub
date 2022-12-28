(ns clj-activitypub.net
  (:require [clj-activitypub.internal.thread-cache :as thread-cache]
            [clj-activitypub.internal.crypto :as crypto]
            [clj-activitypub.internal.http-util :as http]
            [clj-http.client :as client]
            [clojure.string :as str]))

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
  [config {:keys [body headers]}]
  (let [digest (http/digest body)
        headers (cond-> headers
                  (not (contains? headers "Date")) (assoc "Date" (http/date)))
        headers' (-> headers
                     (assoc "Digest" digest)
                     (assoc "(request-target)" "post /inbox"))]
    (assoc headers
           "Signature" (gen-signature-header config headers')
           "Digest" digest)))

(def ^:private user-cache (thread-cache/make))

(defn reset-user-cache!
  "Removes all entries from the user cache, which is populated with results
   from [[fetch-users]] or [[fetch-user]]."
  []
  ((:reset user-cache)))

(defn fetch-users!
  "Fetches the actor(s) located at remote-id from a remote server. Results are
   returned as a collection. If URL points to an ActivityPub Collection, the
   links will be followed until max-depth is reached (default 3). Will return
   cached results if they exist in memory."
  ([remote-id] (fetch-users! remote-id 3 0))
  ([remote-id max-depth] (fetch-users! remote-id max-depth 0))
  ([remote-id max-depth current-depth]
   (when (< current-depth max-depth)
     ((:get-v user-cache)
      remote-id
      (fn []
        (when-let [response (client/get remote-id http/GET-config)]
          (let [depth' (inc current-depth)
                body (:body response)
                type (:type body)]
            (condp some (if (coll? type) type [type])
              #{"Person" "Service"} [body]
              #{"OrderedCollection" "Collection"} (fetch-users! (:first body) max-depth depth')
              #{"OrderedCollectionPage" "CollectionPage"}
              (cond-> #(fetch-users! % max-depth depth')
                (or (:orderedItems body)
                    (:items body)) (map (or (:orderedItems body) (:items body)))
                (:next body) (concat (fetch-users! (:next body) max-depth current-depth)))))))))))

(defn fetch-user!
  "Fetches the actor located at user-id from a remote server. Links to remote
   ActivityPub Collections will not be followed. If you wish to retrieve a list
   of users, see [[fetch-users]]. Will return a cached result if it exists in
   memory."
  [user-id]
  (first (fetch-users! user-id 1)))

(defn delivery-targets!
  "Returns the distinct inbox locations for the audience of the activity.
   Following the ActivityPub spec, this includes the :to, :bto, :cc, :bcc, and
   :audience fields while also removing the author's own address. If the user's
   server supports a sharedInbox, that location is returned instead."
  [activity]
  (let [actor-id (:actor activity)
        remote-ids (-> activity
                       (select-keys [:to :bto :cc :bcc :audience])
                       (vals)
                       (flatten)
                       (distinct))]
    (->> remote-ids
         (mapcat fetch-users!)
         (group-by #(or (get-in % [:endpoints :sharedInbox])
                        (get % :inbox)))
         (keys)
         (distinct)
         (remove #(= % actor-id)))))