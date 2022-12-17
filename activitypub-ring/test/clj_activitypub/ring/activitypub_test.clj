(ns clj-activitypub.ring.activitypub-test
  (:require [clojure.test :as t :refer (is deftest testing)]
            [clojure.data.json :as json]
            [ring.mock.request :as mock]
            [clj-activitypub.core :as activitypub]
            [clj-activitypub.ring.activitypub :as ring-activitypub]))

(def example-domain "example.com")

(def routes
  (ring-activitypub/user-routes example-domain))

(deftest user
  (testing "/inbox route"
    (is (= {:status 200
            :headers {"Content-Type" "application/jrd+json; charset=utf-8"}
            :body (json/write-str {"@context" ["https://www.w3.org/ns/activitystreams"]
                                   "id" "https://localhost/users/jahfer/inbox?"
                                   "type"	"OrderedCollectionPage"
                                   "orderedItems" []})}
           (routes (mock/request :post "/users/jahfer/inbox")))))
  
  (testing "/outbox route"
    (is (= {:status 200
            :headers {"Content-Type" "application/jrd+json; charset=utf-8"}
            :body (json/write-str {"@context" ["https://www.w3.org/ns/activitystreams"]
                                   "id" "https://localhost/users/jahfer/outbox?"
                                   "type"	"OrderedCollectionPage"
                                   "orderedItems" []})}
           (routes (mock/request :get "/users/jahfer/outbox")))))
  
  (testing "/cards/:id route"
    (with-redefs []
      (is (= {:status 200
              :headers {"Content-Type" "application/jrd+json; charset=utf-8"}
              :body "{}"}
             (routes (mock/request :get "/users/jahfer/cards/1"))))))
  
  (testing "/ route"
    (let [user-id (str "https://" example-domain "/users/jahfer")]
      (is (= {:status 200
              :headers {"Content-Type" "application/jrd+json; charset=utf-8"}
              :body (json/write-str
                     {"@context" ["https://www.w3.org/ns/activitystreams"
                                  "https://w3id.org/security/v1"]
                      "id" user-id
                      "type" "Person"
                      "preferredUsername" "jahfer"
                      "inbox" (str user-id "/inbox")
                      "outbox" (str user-id "/outbox")
                      "publicKey" {"id" (str user-id "#main-key")
                                   "owner" user-id
                                   "publicKeyPem" ""}})}
             (routes (mock/request :get "/users/jahfer")))))))