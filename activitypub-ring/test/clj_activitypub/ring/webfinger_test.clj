(ns clj-activitypub.ring.webfinger-test
  (:require [clojure.test :as t :refer (is deftest testing)]
            [clojure.data.json :as json]
            [ring.mock.request :as mock]
            [clj-activitypub.ring.webfinger :as ring-webfinger]))

(def example-domain "example.com")

(def routes
  (ring-webfinger/routes {:domain example-domain}))

(deftest handler
  (testing "/.well-known/webfinger"
    (let [username "jahfer"]
      (is (= {:status 200
              :headers {"Content-Type" "application/jrd+json; charset=utf-8"}
              :body (json/write-str
                     {"subject" (str "acct:" username "@" example-domain)
                      "links" [{"rel" "self"
                                "type" "application/activity+json"
                                "href" (str "https://" example-domain "/users/" username)}]})}
             (routes (mock/request :get "/.well-known/webfinger"
                                   {"resource" (str "acct:" username "@example.com")})))))))