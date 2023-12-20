(ns clj-activitypub.core-test
  (:require [clojure.test :as t :refer (is deftest testing)]
            [clj-activitypub.core :as core]
            [clj-activitypub.test-support.assert :refer (=?)]))

(defn mock-date []
  (str "2022-12-27T20:46:05.915189Z"))

(deftest config
  (testing "#config creates hash of expected results"
    (let [data {:domain "example.com"
                :username "jahfer"
                :public-key (slurp "../keys/test_public.pem")}]
      (is (=? {:domain "example.com"
               :username "jahfer"
               :base-url "https://example.com"
               :user-id "https://example.com/users/jahfer"
               :public-key "-----BEGIN PUBLIC KEY-----\nMIIBIjANBgkqhkiG9w0BAQEFAAOCAQ8AMIIBCgKCAQEA4Q0Y5nUeh/YLrdhe0rhF\nlLMOjqzaA326XanjkeMbF8QggSLF9t+9t/zzDnXFK8ESIaiEYSaFf0jQbNBU2uAu\nuXKaVOlATAR1UQ65Z3Fj3lWbs+fR3Ku7WM49hPR889onDC3K7Smnd1hXeFfbcxfl\niGbUkwcgjdETxwjzIuPY4l6287uwSGSb1Ch0GLNxb6cFHDlO4rtd60rs9sUd/ppe\n1yr7FI5fbtr3iiEGCmqMUcmywIzxUkmGcy7JjweP/oEb8mdpMRlKw7jz/fjnJL6z\nQImiivJU6tx8a0mDQn6mm1ndtVOlMK2kivcjhWKitL8o6DQWLC63MPaOEJDWOx3J\nOwIDAQAB\n-----END PUBLIC KEY-----\n"}
              (select-keys (core/config data)
                           [:domain :username :base-url :user-id :public-key]))))))

(deftest actor
  (testing "actor returns expected hash"
    (let [config (core/config {:domain "example.com" :username "jahfer"})]
      (is (=? {"@context" ["https://www.w3.org/ns/activitystreams"
                           "https://w3id.org/security/v1"]
               "id" "https://example.com/users/jahfer"
               "type" "Person"
               "name" "jahfer"
               "preferredUsername" "jahfer"
               "inbox" "https://example.com/users/jahfer/inbox"
               "outbox" "https://example.com/users/jahfer/outbox"
               "following" "https://example.com/users/jahfer/following"
               "followers" "https://example.com/users/jahfer/followers"
               "publicKey" {"id" "https://example.com/users/jahfer#main-key"
                            "owner" "https://example.com/users/jahfer"
                            "publicKeyPem" ""}}
             (core/actor config))))))

(deftest instance-actor
  (testing "instance-actor returns expected hash"
    (let [config (core/config {:domain "example.com" :username "jahfer"})]
      (is (=? {"@context" ["https://www.w3.org/ns/activitystreams"
                           "https://w3id.org/security/v1"]
               "id" "https://example.com/users/jahfer"
               "type" "Application"
               "url" "https://example.com/users/jahfer"
               "preferredUsername" "instance.actor"
               "inbox" "https://example.com/users/jahfer/inbox"
               "outbox" "https://example.com/users/jahfer/outbox"
               "publicKey" {"id" "https://example.com/users/jahfer#main-key"
                            "owner" "https://example.com/users/jahfer"
                            "publicKeyPem" ""}}
              (core/instance-actor config))))))

(deftest obj
  (testing "obj :note returns expected hash"
    (let [config (core/config {:domain "example.com" :username "jahfer"})
          {:keys [obj]} (core/with-config config)]
      (is (=? {"id" "https://example.com/users/jahfer/notes/1"
               "type" "Note"
               "published" (mock-date)
               "attributedTo" "https://example.com/users/jahfer"
               "inReplyTo" ""
               "replies" []
               "content" "Hello world!"
               "to" "https://www.w3.org/ns/activitystreams#Public"
               "cc" []}
             (with-redefs [core/date mock-date]
               (obj {:id 1 :type :note :content "Hello world!"})))))))

(deftest activity
  (testing "activity :create returns expected hash"
    (let [config (core/config {:domain "example.com" :username "jahfer"})]
      (is (=? {"@context" ["https://www.w3.org/ns/activitystreams"
                           "https://w3id.org/security/v1"]
               "type" "Create"
               "actor" "https://example.com/users/jahfer"
               "published" nil
               "to" nil
               "cc" nil
               "object" {"my" "object"}}
             (core/activity config :create {"my" "object"})))))

  (testing "activity :delete returns expected hash"
    (let [config (core/config {:domain "example.com" :username "jahfer"})]
      (is (=? {"@context" ["https://www.w3.org/ns/activitystreams"
                           "https://w3id.org/security/v1"]
               "type" "Delete"
               "actor" "https://example.com/users/jahfer"
               "to" nil
               "cc" nil
               "object" {"my" "object"}}
             (core/activity config :delete {"my" "object"}))))))