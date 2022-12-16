(ns clj-activitypub.internal.thread-cache-test
  (:require [clojure.test :as t :refer (is deftest testing)]
            [clj-activitypub.internal.thread-cache :as tc]))

(deftest cache-if-nil
  (testing "Defaults to :cache-if-nil false"
    (is (= false
           (:cache-if-nil (tc/make)))))
  (testing "Sets :cache-if-nil true if specified"
    (is (= true
           (:cache-if-nil (tc/make true)))))
  (testing "Sets :cache-if-nil false if specified"
    (is (= false
           (:cache-if-nil (tc/make false))))))

(deftest get-v
  (testing "get-v returns nil if value is not in cache"
    (let [cache (tc/make)]
      (is (nil? ((:get-v cache) "does-not-exist")))))
  (testing "get-v returns value when inserting into empty cache"
    (let [cache (tc/make)]
      (is (= 1 ((:get-v cache) "my-key" #(do 1))))))
  (testing "get-v runs compute-fn once"
    (let [v (ref 0)
          compute-fn #(dosync (alter v inc))
          cache (tc/make)]
      (is (= 1 ((:get-v cache) "my-key" compute-fn)))
      (is (= 1 ((:get-v cache) "my-key" compute-fn))))))

(deftest cache-kv
  (testing "cache-kv stores value in cache, returning value"
    (let [cache (tc/make)
          expected-value "New value!"
          result ((:cache-kv cache) "my-key" expected-value)]
      (is (= expected-value result))
      (is (= expected-value ((:get-v cache) "my-key")))))
  (testing "cache-kv will replace existing value at key"
    (let [cache (tc/make)
          old-value "Old value!"
          new-value "New value!"
          first-result ((:cache-kv cache) "my-key" old-value)
          second-result ((:cache-kv cache) "my-key" new-value)]
      (is (= old-value first-result))
      (is (= new-value second-result))
      (is (= new-value ((:get-v cache) "my-key"))))))

(deftest lru
  (testing "lru returns key-value pairs sorted by least-recently-used"
    (let [cache (tc/make)]
      ((:cache-kv cache) :key-1 "Hello")
      (Thread/sleep 1) ;; sleep for 1ms for determinism in sort order
      ((:cache-kv cache) :key-2 "World")
      (is (= [[:key-1 "Hello"] [:key-2 "World"]]
             ((:lru cache))))

      (Thread/sleep 1)
      ((:cache-kv cache) :key-1 "Hola")
      (is (= [[:key-2 "World"] [:key-1 "Hola"]]
             ((:lru cache)))))))