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
                   "Another" 123}
          body "Hello world!"]
      (is (=? (with-redefs [http/date mock-date]
                (net/auth-headers
                 (core/config {:domain "example.com"
                               :username "jahfer"
                               :private-key (slurp "../keys/test_private.pem")})
                 {:headers headers :body body :request-target "post /index"}))
              {"Test" "header example"
               "Another" 123
               "Date" (mock-date)
               "Signature" "keyId=\"https://example.com/users/jahfer#main-key\",algorithm=\"rsa-sha256\",headers=\"(request-target) host date digest\",signature=\"wLp10mCTgQSgQlwELbh4QtWMiligbfcZv3573TLUrUJEYlWfQT+mmqGpMDZeWxGpPBU8oIK4JWThb1nqFVmGSNIkGv5RDMWhHbuEksNNNGe69q8NjHH/fMtFcrIEtAjKU7iFnOOruGqDvKDjM+L7YCs8Mo5Nph61GBRbgv5wWQKiTA1iT5CLgpjFmYaVXYWN11gfirkliCT0sbzfsSLO2WEtdLwxeY7d9kmCH81m9lqN0CQnLxT5Z/tZVKJz249eY1MlMFuaIn0wLEWMNq2gUeK2Qg99MzVShj7JtuqzSWSuYCFgYZ6nopTRAA0E1IrLEEHtm21HX9ZwdPAEotS2JQ==\""
               "Digest" "sha-256=wFNeS+K3n/2TKRMFQ2v4iTFOSj+uwF7P/Lt98xrZ5Ro="})))))