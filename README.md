# clj-activitypub

⚠️ WIP!

#### Examples

```clj
;; Fetching user account on remote server
(as-> "@jahfer@mastodon.social" $
  (ap/parse-account $)
  (map $ [:domain :username])
  (apply webfinger/fetch-user-id $)
  (ap/fetch-user $)
  (select-keys $ ["followers" "following" "inbox" "outbox"
                  "endpoints" "publicKey" "summary" "attachment"
                  "name" "preferredUsername" "icon" "published"]))
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
                      :headers (activitypub/auth-headers config request
                      :throw-exceptions false))))
```

#### Running tests

```bash
$ clj -X:test
```

#### Reference
- https://www.w3.org/TR/activitypub/
- https://www.w3.org/TR/activitystreams-core/
- https://www.w3.org/ns/activitystreams#Public