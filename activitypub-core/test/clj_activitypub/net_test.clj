(ns clj-activitypub.net-test
  (:require [clojure.test :as t :refer (is deftest testing)]
            [clj-activitypub.core :as core]
            [clj-activitypub.net :as net]
            [clj-activitypub.internal.http-util :as http]
            [clj-activitypub.test-support.http :refer [http-stubs]]
            [clj-http.fake :refer [with-fake-routes-in-isolation]]))

(defn mock-date []
  (str "Tue, 29 Nov 2022 12:47:08 GMT"))

(deftest fetch-actor!
  (testing "Performs GET request, returning the response body"
    (net/reset-object-cache!)
    (with-fake-routes-in-isolation http-stubs
      (let [user-id "https://example.com/users/jahfer"]
        (is (= {:inbox "https://example.com/users/jahfer/inbox"
                :outbox "https://example.com/users/jahfer/outbox"
                :name "Jahfer"}
               (select-keys (net/fetch-actor! user-id)
                            [:inbox :outbox :name]))))))
  (testing "Retrieves data from cache if exists"
    (net/reset-object-cache!)
    (let [user-id "https://example.com/users/jahfer"]
      (with-fake-routes-in-isolation http-stubs
        (net/fetch-actor! user-id)) ;; call once with stub to cache results
      (with-fake-routes-in-isolation {}
        (net/fetch-actor! user-id)))))

(deftest resolve!
  (testing "Performs GET request, returning the response body"
    (net/reset-object-cache!)
    (with-fake-routes-in-isolation http-stubs
      (let [user-id "https://example.com/users/jahfer"]
        (is (= [{:inbox "https://example.com/users/jahfer/inbox"
                 :outbox "https://example.com/users/jahfer/outbox"
                 :name "Jahfer"}]
               (map #(select-keys % [:inbox :outbox :name])
                    (net/resolve! user-id)))))))
  (testing "Retrieves data from cache if exists"
    (net/reset-object-cache!)
    (let [user-id "https://example.com/users/jahfer"]
      (with-fake-routes-in-isolation http-stubs
         ;; call once with stub to cache results
        (net/resolve! user-id))
      (with-fake-routes-in-isolation {}
        (net/resolve! user-id)))))

(deftest delivery-targets!)

(deftest auth-headers
  (testing "Accepts request data and returns the headers with auth attributes included"
    (let [headers {"Test" "header example"
                   "Another" 123}
          body "Hello world!"]
      (is (= (with-redefs [http/date mock-date]
               (net/auth-headers
                (core/config {:domain "example.com"
                              :username "jahfer"
                              :private-key (slurp "../keys/test_private.pem")})
                {:headers headers :body body}))
             {"Test" "header example"
              "Another" 123
              "Date" (mock-date)
              "Signature" "keyId=\"https://example.com/users/jahfer\",headers=\"(request-target) host date digest\",signature=\"bJ2owLjMKxrXpqh4Ajrq8ZZlz8/DncJdkv1Pdtw3XTwIZca3RUJz3UW1US72kBf1o6jkpNni9i9lEC8S8lCYhfmT9zsnWz+NyXaZqeTaG7heD7vd8UuUMfNEUAnncfZWgCqXxdUtqrAvlJkFbxAm6ChcehU/mOrdet8HdDsaHwyT9JSmap130sT+S9tZk3Kejwt6lrL829bYsQ302VKedHb12rRH8K0QusYnbzEw110h6XP4ciFSO2I71JveATYPM5tIW5yQhc0pBzqfn7qrWYXE4uSx3up3JF8aOWg0ivdjBmLZR6n6tex3TgXfYX+DIaQ3Ckg5ccdArs9ohOXKGQ==\""
              "Digest" "sha-256=wFNeS+K3n/2TKRMFQ2v4iTFOSj+uwF7P/Lt98xrZ5Ro="})))))