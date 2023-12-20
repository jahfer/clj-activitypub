(ns clj-activitypub.ring.activitypub-test
  (:require [clojure.test :as t :refer (is deftest testing)]
            [clojure.data.json :as json]
            [ring.mock.request :as mock]
            [compojure.core :refer [GET]]
            [clj-activitypub.ring.activitypub :as ring-activitypub]))

(def example-domain "example.com")

(def routes
  (ring-activitypub/user-routes example-domain))

(deftest instance-actor
  (testing "instance actor handler"
    (is (= {:status 200
            :headers {"Content-Type" "application/activity+json"}
            :body (json/write-str {"@context" ["https://www.w3.org/ns/activitystreams"
                                               "https://w3id.org/security/v1"]
                                   "id" "https://example.com/actor"
                                   "type"	"Application"
                                   "preferredUsername" "instance.actor"
                                   "inbox" "https://example.com/actor/inbox"
                                   "outbox" "https://example.com/actor/outbox"
                                   "url" "https://example.com/actor"
                                   "publicKey" {"id" "https://example.com/actor#main-key"
                                                "owner" "https://example.com/actor"
                                                "publicKeyPem" ""}})}
           ((compojure.core/routes
             (GET "/actor" req
               (ring-activitypub/instance-actor-handler
                req
                {:user-id (str "https://example.com/actor")})))
            (mock/request :get "/actor"))))))

(deftest user
  (testing "/inbox route"
    (is (= {:status 202
            :headers {"Content-Type" "application/activity+json"}
            :body ""}
           (routes (mock/request :post "/users/jahfer/inbox")))))

  (testing "/outbox route"
    (is (= {:status 200
            :headers {"Content-Type" "application/activity+json"}
            :body (json/write-str {"@context" ["https://www.w3.org/ns/activitystreams"]
                                   "id" "https://localhost/users/jahfer/outbox"
                                   "type"	"OrderedCollection"
                                   "totalItems" 0
                                   "orderedItems" []})}
           (routes (mock/request :get "/users/jahfer/outbox")))))

  (testing "/ route"
    (let [user-id (str "https://" example-domain "/users/jahfer")]
      (is (= {:status 200
              :headers {"Content-Type" "application/activity+json"}
              :body (json/write-str
                     {"@context" ["https://www.w3.org/ns/activitystreams"
                                  "https://w3id.org/security/v1"]
                      "id" user-id
                      "type" "Person"
                      "name" "jahfer"
                      "preferredUsername" "jahfer"
                      "inbox" (str user-id "/inbox")
                      "outbox" (str user-id "/outbox")
                      "following" (str user-id "/following")
                      "followers" (str user-id "/followers")
                      "publicKey" {"id" (str user-id "#main-key")
                                   "owner" user-id
                                   "publicKeyPem" ""}})}
             (routes (mock/request :get "/users/jahfer")))))))