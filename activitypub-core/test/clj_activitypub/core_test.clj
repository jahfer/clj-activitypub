(ns clj-activitypub.core-test
  (:require [clojure.test :as t :refer (is deftest testing)]
            [clj-activitypub.core :as core]
            [clj-activitypub.internal.http-util :as http-util]
            [clj-activitypub.test-support.http :refer [http-stubs]]
            [clj-http.fake :refer [with-fake-routes-in-isolation]]))

(defn mock-date []
  (str "Tue, 29 Nov 2022 12:47:08 GMT"))

(deftest config
  (testing "#config creates hash of expected results"
    (let [data {:domain "example.com"
                :username "jahfer"
                :public-key (slurp "../keys/test_public.pem")}]
      (is (= {:domain "example.com"
              :username "jahfer"
              :base-url "https://example.com"
              :user-id "https://example.com/users/jahfer"
              :public-key "-----BEGIN PUBLIC KEY-----\nMIIBIjANBgkqhkiG9w0BAQEFAAOCAQ8AMIIBCgKCAQEA4Q0Y5nUeh/YLrdhe0rhF\nlLMOjqzaA326XanjkeMbF8QggSLF9t+9t/zzDnXFK8ESIaiEYSaFf0jQbNBU2uAu\nuXKaVOlATAR1UQ65Z3Fj3lWbs+fR3Ku7WM49hPR889onDC3K7Smnd1hXeFfbcxfl\niGbUkwcgjdETxwjzIuPY4l6287uwSGSb1Ch0GLNxb6cFHDlO4rtd60rs9sUd/ppe\n1yr7FI5fbtr3iiEGCmqMUcmywIzxUkmGcy7JjweP/oEb8mdpMRlKw7jz/fjnJL6z\nQImiivJU6tx8a0mDQn6mm1ndtVOlMK2kivcjhWKitL8o6DQWLC63MPaOEJDWOx3J\nOwIDAQAB\n-----END PUBLIC KEY-----\n"}
             (select-keys (core/config data)
                          [:domain :username :base-url :user-id :public-key]))))))

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

(deftest fetch-users 
  (testing "Performs GET request, returning the response body"
    (core/reset-user-cache)
    (with-fake-routes-in-isolation http-stubs
      (let [user-id "https://example.com/users/jahfer"]
        (is (= [{:inbox "https://example.com/users/jahfer/inbox"
                 :outbox "https://example.com/users/jahfer/outbox"
                 :name "Jahfer"}]
               (map #(select-keys % [:inbox :outbox :name])
                    (core/fetch-users user-id)))))))
  (testing "Retrieves data from cache if exists"
    (core/reset-user-cache)
    (let [user-id "https://example.com/users/jahfer"]
      (with-fake-routes-in-isolation http-stubs
        (core/fetch-users user-id)) ;; call once with stub to cache results
      (with-fake-routes-in-isolation {}
        (core/fetch-users user-id)))))

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