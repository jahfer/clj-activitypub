#!/bin/bash
panic() {
  printf "%s: %.0s${1}\n" "${0##*/}" "$@" >&2
  exit 1
}

clj -T:build clean || panic "Failed to clean /target"
clj -T:build jar || panic "Failed to build jar"