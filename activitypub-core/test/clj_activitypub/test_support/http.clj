(ns clj-activitypub.test-support.http)

(def http-stubs
  {#"https://example.com/users/([a-zA-Z]+)"
   (fn [_request]
     {:status 200
      :headers {"content-type" "application/activity+json; charset=utf-8"
                "date" "Fri, 16 Dec 2022 22:23:37 GMT"}
      :body (slurp "test/clj_activitypub/test_support/actor_response.json")})
   
   "https://example.com/.well-known/webfinger?rel=self&resource=acct%3Ajahfer%40example.com"
   (fn [_request]
     {:status 200
      :headers {"content-type" "application/jrd+json; charset=utf-8"}
      :body (slurp "test/clj_activitypub/test_support/webfinger_response.json")})})