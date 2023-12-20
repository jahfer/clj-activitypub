(ns clj-activitypub.net-test
  (:require [clojure.test :as t :refer (is deftest testing)]
            [clj-activitypub.core :as core]
            [clj-activitypub.net :as net]
            [clj-activitypub.internal.http-util :as http]
            [clj-activitypub.test-support.http :refer [http-stubs]]
            [clj-http.fake :refer [with-fake-routes-in-isolation]]
            [clj-activitypub.test-support.assert :refer (=?)]))

(defn mock-date []
  (str "Tue, 29 Nov 2022 12:47:08 GMT"))

(deftest resolve!
  (testing "Performs GET request, returning the response body"
    (net/reset-cache!)
    (with-fake-routes-in-isolation http-stubs
      (let [user-id "https://example.com/users/jahfer"]
        (is (=? [{:inbox "https://example.com/users/jahfer/inbox"
                  :outbox "https://example.com/users/jahfer/outbox"
                  :name "Jahfer"}]
                (map #(select-keys % [:inbox :outbox :name])
                     (net/resolve! user-id)))))))
  (testing "Retrieves data from cache if exists"
    (net/reset-cache!)
    (let [user-id "https://example.com/users/jahfer"]
      (with-fake-routes-in-isolation http-stubs
         ;; call once with stub to cache results
        (net/resolve! user-id))
      (with-fake-routes-in-isolation {}
        (net/resolve! user-id)))))

(deftest authorized-resolve!
  (testing "Performs GET request, returning the response body"
    (net/reset-cache!)
    (with-fake-routes-in-isolation http-stubs
      (let [user-id "https://example.com/users/jahfer"
            config (core/config {:domain "example.com"
                                 :username "jahfer"
                                 :private-key (slurp "../keys/test_private.pem")})]
        (is (=? [{:inbox "https://example.com/users/jahfer/inbox"
                  :outbox "https://example.com/users/jahfer/outbox"
                  :name "Jahfer"}]
                (map #(select-keys % [:inbox :outbox :name])
                     (net/authorized-resolve! user-id config))))))))

(deftest fetch-actor!
  (testing "Performs GET request, returning the response body"
    (net/reset-cache!)
    (with-fake-routes-in-isolation http-stubs
      (let [user-id "https://example.com/users/jahfer"]
        (is (=? {:inbox "https://example.com/users/jahfer/inbox"
                 :outbox "https://example.com/users/jahfer/outbox"
                 :name "Jahfer"}
                (select-keys (net/fetch-actor! user-id)
                             [:inbox :outbox :name]))))))
  (testing "Retrieves data from cache if exists"
    (net/reset-cache!)
    (let [user-id "https://example.com/users/jahfer"]
      (with-fake-routes-in-isolation http-stubs
        (net/fetch-actor! user-id)) ;; call once with stub to cache results
      (with-fake-routes-in-isolation {}
        (net/fetch-actor! user-id)))))

(deftest authorized-fetch-actor!
  (testing "Performs GET request, returning the response body"
    (net/reset-cache!)
    (with-fake-routes-in-isolation http-stubs
      (let [user-id "https://example.com/users/jahfer"
            config (core/config {:domain "example.com"
                                 :username "jahfer"
                                 :private-key (slurp "../keys/test_private.pem")})]
        (is (=? {:inbox "https://example.com/users/jahfer/inbox"
                 :outbox "https://example.com/users/jahfer/outbox"
                 :name "Jahfer"}
                (select-keys (net/authorized-fetch-actor! user-id config)
                             [:inbox :outbox :name])))))))

(deftest delivery-targets!)

(deftest auth-headers
  (testing "Accepts request data and returns the headers with auth attributes included"
    (let [headers {"Test" "header example"
                   "Another" 123
                   "Host" "example.com"}
          body "Hello world!"]
      (is (=? (with-redefs [http/date mock-date]
                (net/auth-headers
                 (core/config {:domain "example.com"
                               :username "jahfer"
                               :private-key (slurp "../keys/test_private.pem")})
                 {:headers headers :body body :request-target "post /index"}))
              {"Test" "header example"
               "Another" 123
               "Host" "example.com"
               "Date" (mock-date)
               "Signature" "keyId=\"https://example.com/users/jahfer#main-key\",algorithm=\"rsa-sha256\",headers=\"(request-target) host date digest\",signature=\"ZXskBM4s8zICkJxBiM9r4zbW6PFsjmc31T/rYAFJm4S1VKU2Pk20LJxQT/dxdNE4dnutin+YONELSsgfM5XYBkiO+lM7u2zNglvow1io6IZ0Jy7OB00JYbRPW5BSg9hsgCMJQ62++LC+/VuCTqX/CP4P7VGyr8I0/uUuKowPn4Cfp8MKwPOKOjruTmS0mI1oP7KkWPvmL0EShiayKzmlMEK6BaknGuIRUs8Weuo7ysozeVhQ/a0yFOJX823omAiaCasHo2EpZnxo2QCSfXKPdkx6JSB1f2RldINNLmvWJjNhvTfF84kE9tKOaNW4LCPY1nHjt+yzCIMTEitWest5tw==\""
               "Digest" "sha-256=wFNeS+K3n/2TKRMFQ2v4iTFOSj+uwF7P/Lt98xrZ5Ro="})))))