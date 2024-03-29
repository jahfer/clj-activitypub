(ns clj-activitypub.webfinger-test
  (:require [clojure.test :as t :refer (is deftest testing)]
            [clj-activitypub.webfinger :as webfinger]
            [clj-activitypub.test-support.http :refer [http-stubs]]
            [clj-http.fake :refer [with-fake-routes-in-isolation]]
            [clj-activitypub.test-support.assert :refer (=?)]))

(deftest remote-uri-path
  (testing "URI of webfinger path"
    (is (=? "/.well-known/webfinger" webfinger/remote-uri-path))))

(deftest resource-url
  (testing "Builds a URL referencing webfinger information"
    (is (=? "https://example.com/.well-known/webfinger?resource=acct%3Ajahfer%40example.com"
            (webfinger/resource-url "example.com" "jahfer")))))

(deftest parse-handle
  (testing "Parses account to username and domain"
    (is (=? (webfinger/parse-handle "jahfer@example.com")
            {:username "jahfer" :domain "example.com"})))
  (testing "Parses account with leading @-sign"
    (is (=? (webfinger/parse-handle "@jahfer@example.com")
            {:username "jahfer" :domain "example.com"})))
  (testing "Parses username when domain is not provided"
    (is (=? (webfinger/parse-handle "@jahfer")
            {:username "jahfer" :domain nil}))))

(deftest fetch-user-id
  (testing "Retrieves user-id from remote webfinger endpoint"
    (webfinger/reset-user-id-cache!)
    (with-fake-routes-in-isolation http-stubs
      (is (=? "https://example.com/users/jahfer"
              (webfinger/fetch-user-id! :domain "example.com" :username "jahfer")))))
  (testing "Retrieves cached results without performing a network call"
    (webfinger/reset-user-id-cache!)
    (with-fake-routes-in-isolation http-stubs
       ;; call once with stub to cache results
      (webfinger/fetch-user-id! :domain "example.com" :username "jahfer"))
    (with-fake-routes-in-isolation {}
      (webfinger/fetch-user-id! :domain "example.com" :username "jahfer"))))