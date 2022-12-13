(ns clj-activitypub.core-test
  (:require [clojure.test :as t :refer (is deftest testing)]
            [clj-activitypub.core :as core]
            [clj-activitypub.internal.http-util :as http-util]))

(defn mock-date []
  (str "Tue, 29 Nov 2022 12:47:08 GMT"))

(deftest config
  (testing "#config creates hash of expected results"
    (let [data {:domain "example.com"
                :username "jahfer"}]
      (is (= {:domain "example.com"
              :username "jahfer"
              :base-url "https://example.com"
              :user-id "https://example.com/users/jahfer"}
             (select-keys (core/config data)
                          [:domain :username :base-url :user-id]))))))

(deftest parse-account
  (testing "Parses account to username and domain"
    (is (= (core/parse-account "jahfer@example.com") 
           {:username "jahfer" :domain "example.com"}))) 
  (testing "Parses account with leading @-sign"
    (is (= (core/parse-account "@jahfer@example.com")
           {:username "jahfer" :domain "example.com"})))
  (testing "Parses username when domain is not provided"
    (is (= (core/parse-account "@jahfer")
           {:username "jahfer" :domain nil}))))

(deftest actor)

(deftest fetch-user)

(deftest auth-headers
  (testing "Accepts request data and returns the headers with auth attributes included"
    (let [headers {"Test" "header example"
                   "Another" 123}
          body "Hello world!"]
      (is (= (core/auth-headers
              (core/config (assoc (core/parse-account "@jahfer@example.com")
                                  :private-key (slurp "../keys/test_private.pem")))
              {:headers headers :body body})
             {"Test" "header example"
              "Another" 123
              "Signature" "keyId=\"https://example.com/users/jahfer\",headers=\"(request-target) host date digest\",signature=\"CFrLNZJNvo0/w94nnL29m0zZpIgqKRuv4QhRQ2HSXBf1AB3Y4OfdUifwlKwwT9RK8fibOkvWUdut8XD8OE82gMGVlZ6WpmiYt8OFo4lKpkxJxriGX0uRP0UyO4GZqgqBC1peW7LTOFfjwjYutwxtrB9gl3me8YPN5GhnLnwVkh1k9deYlqJsDRChPmUVnPwnv8lYdK9igBLrJWd3o0BhJVtD8gV2XX/5TGxjFvkgBVlpzVF9HFJwGLTaz0g0Xb4ny8KOn6CmLPb37EOTc8YABv0TbtqVDXlTygmFyJUu/19dOaOrIBCCKQkjOgVLYs4M4YMt+4Hc6ri/D3xr3CGssA==\""
              "Digest" "sha-256=wFNeS+K3n/2TKRMFQ2v4iTFOSj+uwF7P/Lt98xrZ5Ro="})))))

(deftest obj
  (testing "obj :note returns expected hash" 
    (let [config (core/config (core/parse-account "@jahfer@example.com"))
          {:keys [obj]} (core/with-config config)]
      (is (= {"id" "https://example.com/users/jahfer/notes/1"
              "type" "Note" 
              "published" (mock-date)
              "attributedTo" "https://example.com/users/jahfer"
              "inReplyTo" ""
              "content" "Hello world!"
              "to" "https://www.w3.org/ns/activitystreams#Public"}
             (with-redefs [http-util/date mock-date]
               (obj {:id 1 :type :note :content "Hello world!"})))))))

(deftest activity
  (testing "activity :create returns expected hash"
    (let [config (core/config (core/parse-account "@jahfer@example.com"))]
      (is (= {"@context" ["https://www.w3.org/ns/activitystreams"
                          "https://w3id.org/security/v1"]
              "type" "Create"
              "actor" "https://example.com/users/jahfer"
              "object" {"my" "object"}}
             (core/activity config :create {"my" "object"})))))
  
  (testing "activity :delete returns expected hash"
    (let [config (core/config (core/parse-account "@jahfer@example.com"))]
      (is (= {"@context" ["https://www.w3.org/ns/activitystreams"
                          "https://w3id.org/security/v1"]
              "type" "Delete"
              "actor" "https://example.com/users/jahfer"
              "object" {"my" "object"}}
             (core/activity config :delete {"my" "object"}))))))