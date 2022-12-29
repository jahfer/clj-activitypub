(ns clj-activitypub.internal.thread-cache)

(defn- current-time 
  "Returns current time using UNIX epoch."
  []
  (System/currentTimeMillis))

(defn- update-read-at [store k v]
  (dosync
   (commute store assoc k
            (merge v {:read-at (current-time)}))))

(defn make 
  "Creates a thread-local cache."
  ([] (make false))
  ([cache-if-nil]
   (let [store (ref {})]
     (letfn [(cache-kv ([k v]
                        (dosync
                         (commute store assoc k
                                  {:write-at (current-time) 
                                   :read-at (current-time) 
                                   :value v})
                         v)))
             (get-v ([k]
                     (when-let [data (get @store k)]
                       (update-read-at store k data)
                       (:value data)))
                    ([k compute-fn]
                     (let [storage @store]
                       (if (contains? storage k)
                         (get-v k)
                         (let [v (compute-fn)]
                           (when (or (not (nil? v)) cache-if-nil)
                             (cache-kv k v)
                             (get-v k)))))))
             (lru ([]
                   (mapv
                    (fn [[k v]] [k (:value v)])
                    (sort-by #(-> % val :read-at) < @store))))]
       {:cache-kv cache-kv 
        :get-v get-v
        :cache-if-nil cache-if-nil
        :lru lru
        :reset (fn [] (dosync (ref-set store {})))}))))