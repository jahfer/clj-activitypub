# clj-activitypub

> **Warning**
> This is very much a work-in-progress. Only a tiny bit of the ActivityPub spec is implemented, and it definitely does not conform to all of the nuances expected _yet_.

#### Examples

Before POSTing data to a remote server, you'll need to create a local key/value pair in the `/keys` directory.

```bash
$ openssl genrsa -out keys/private.pem 2048
$ openssl rsa -in keys/private.pem -outform PEM -pubout -out keys/public.pem
```

```clj
;; Fetching user account on remote server
(require '[clj-activitypub.core :as activitypub])
(require '[clj-activitypub.webfinger :as webfinger])
(require '[clojure.walk :refer [keywordize-keys]])
(require '[clojure.pprint :refer [pprint]])

;;; Use any ActivityPub account handle you like - for example, your own
(def account-handle "@simon_brooke@mastodon.scot")

;;; Retrieve the account details from its home server
;;; (`keywordize-keys` is not necessary here but produces a more idiomatic clojure
;;; data structure)
(def account 
 (as-> account-handle $
   (activitypub/parse-account $)
   (map $ [:domain :username])
   (apply webfinger/fetch-user-id $)
   (activitypub/fetch-user $)
   (select-keys $ ["followers" "following" "inbox" "outbox"
                   "endpoints" "publicKey" "summary" "attachment"
                   "name" "preferredUsername" "icon" "published"])
   (keywordize-keys $)))

;;; examine what you got back!
(pprint account)
```

```clj
;; Submitting a Create activity for an Object to remote server
(let [config (activitypub/config {:domain base-domain :username "jahfer"})
      {:keys [activity obj]} (activitypub/with-config config)
      body (->> (obj {:id 1
                      :type :note
                      :content "Hello world!"})
                (activity :create))
      request {:headers {"Host" "mastodon.social"
                         "Date" (http/date)}
               :body (json/write-str body)}]
  (client/post "https://mastodon.social/inbox"
               (assoc request
                      :headers (activitypub/auth-headers config request)
                      :throw-exceptions false)))
```

#### Running tests

> **Note**
> Tests requiring access to the public/private key will currently fail as the keys are not committed to the repo.

```bash
$ cd activitypub-core
$ clj -X:test
$ cd ../activitypub-ring
$ clj -X:test
```

#### Reference
- https://www.w3.org/TR/activitypub/
- https://www.w3.org/TR/activitystreams-core/
- https://www.w3.org/ns/activitystreams#Public
