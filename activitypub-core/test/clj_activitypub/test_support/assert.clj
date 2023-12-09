(ns clj-activitypub.test-support.assert
  (:require [lambdaisland.deep-diff2 :as ddiff]))

(defn =? [a b]
  (or (= a b)
      (do
        (-> (ddiff/diff a b)
            (ddiff/minimize)
            (ddiff/pretty-print))
        false)))