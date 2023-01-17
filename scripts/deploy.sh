#!/bin/bash
panic() {
  printf "%s: %.0s${1}\n" "${0##*/}" "$@" >&2
  exit 1
}

TOKEN="$(ejson decrypt secrets/deploy.ejson | jq -r '.clojars_token')"

env CLOJARS_USERNAME=jahfer CLOJARS_PASSWORD="$TOKEN" clj -T:build deploy || panic "Failed to deploy"