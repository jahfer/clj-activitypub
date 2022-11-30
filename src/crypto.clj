(ns crypto
  (:require [clojure.java.io :as io])
  (:import (java.util Base64)
           (java.security MessageDigest SecureRandom Signature)))

(java.security.Security/addProvider
 (org.bouncycastle.jce.provider.BouncyCastleProvider.))

(defn- keydata [reader]
  (->> reader
       (org.bouncycastle.openssl.PEMParser.)
       (.readObject)))

(defn- pem-string->key-pair [string]
  (let [kd (keydata (io/reader (.getBytes string)))]
    (.getKeyPair (org.bouncycastle.openssl.jcajce.JcaPEMKeyConverter.) kd)))

(defn private-key [private-pem-str]
  (-> private-pem-str
      (pem-string->key-pair)
      (.getPrivate)))

(defn base64-encode [bytes]
  (.encodeToString (Base64/getEncoder) bytes))

(defn sha256-base64 [data]
  (let [digest (.digest (MessageDigest/getInstance "SHA-256") (.getBytes data))]
    (base64-encode digest)))

(defn sign [data private-key] 
  (let [bytes (.getBytes data)
        signer (doto (Signature/getInstance "SHA256withRSA")
              (.initSign private-key (SecureRandom.))
              (.update bytes))]
    (.sign signer)))

