# clj-activitypub

> **Warning**
> This is very much a work-in-progress. Only a tiny bit of the ActivityPub spec is implemented, and it definitely does not conform to all of the nuances expected _yet_.

`clj-activitypub` is intended as a set of utilities that can be combined together to create a fully-functional ActivityPub server.

### Libraries
- `activitypub-core` — The base functionality for generating HTTP headers (i.e. `Signature`, `Digest`), building ActivityPub activities and objects, and sending requests to remote servers.
- `activitypub-ring` — A Ring-specific implementation that builds on `activitypub-core`, providing default routes and handlers for making an ActivityPub-compliant server.

### Examples

Before POSTing data to a remote server, you'll want to create a local key/value pair in the `/keys` directory.

```bash
$ openssl genrsa -out keys/private.pem 2048
$ openssl rsa -in keys/private.pem -outform PEM -pubout -out keys/public.pem
```

```clj
;; Fetching user account on remote server
(require '[clj-activitypub.core :as activitypub])
(require '[clj-activitypub.webfinger :as webfinger])
(require '[clojure.pprint :refer [pprint]])

;;; Use any ActivityPub account handle you like
(def account-handle "@jahfer@mastodon.social")

;;; Retrieve the account details from its home server
(def account
 (-> account-handle
   (activitypub/parse-handle)
   (webfinger/fetch-user-id)
   (activitypub/fetch-user)
   (select-keys [:name :preferredUsername :summary])))

;;; examine what you got back!
(pprint account) ;; => ({:name "Jahfer",
                 ;;      :preferredUsername "jahfer",
                 ;;      :summary "<p>Hello world!</p>"})
```

```clj
;; Submitting a Create activity for an Object to remote server
(let [config (activitypub/config {:domain base-domain :username "jahfer"})
      body (->> (obj config {:id 1 :type :note :content "Hello world!"})
                (activity config :create))
      request {:headers {"Host" "mastodon.social" "Date" (http/date)}
               :body (json/write-str body)}]
  (client/post "https://mastodon.social/inbox"
               (assoc request
                      :headers (activitypub/auth-headers config request)
                      :throw-exceptions false)))
```

### Running tests

There are two libraries within this package: `activitypub-core` and `activitypub-ring`. In order to run tests, the following command must be run from inside either of those directories:

```bash
# clj_activitypub/activitypub_core
$ clj -X:test
```

### Reference
- https://www.w3.org/TR/activitypub/
- https://www.w3.org/TR/activitystreams-core/
- https://www.w3.org/ns/activitystreams#Public
- https://www.w3.org/TR/activitystreams-vocabulary/
