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
    (is (= {:status 202
            :headers {"Content-Type" "application/jrd+json; charset=utf-8"}
            :body ""}
           (routes (mock/request :post "/users/jahfer/inbox")))))
  
  (testing "/outbox route"
    (is (= {:status 200
            :headers {"Content-Type" "application/jrd+json; charset=utf-8"}
            :body "{}"}
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
             (routes (mock/request :get "/users/jahfer")))))) 
  
  (testing "/ route with :activity-user set"
    (let [user (activitypub/config {:domain example-domain
                                    :username "jahfer"
                                    :public-key (slurp "../keys/test_public.pem")})]
      (is (= {:status 200
              :headers {"Content-Type" "application/jrd+json; charset=utf-8"}
              :body (json/write-str
                     {"@context" ["https://www.w3.org/ns/activitystreams"
                                  "https://w3id.org/security/v1"]
                      "id" (:user-id user)
                      "type" "Person"
                      "preferredUsername" "jahfer"
                      "inbox" (str (:user-id user) "/inbox")
                      "outbox" (str (:user-id user) "/outbox")
                      "publicKey" {"id" (str (:user-id user) "#main-key")
                                   "owner" (:user-id user)
                                   "publicKeyPem" (:public-key user)}})} 
             (routes (assoc (mock/request :get "/users/jahfer")
                            :activity-user user)))))))