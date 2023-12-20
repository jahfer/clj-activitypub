(ns ^:no-doc clj-activitypub.internal.crypto
  (:require [clojure.java.io :as io])
  (:import (java.util Base64)
           (java.io StringReader)
           (java.security KeyFactory MessageDigest SecureRandom Signature)
           (java.security.spec X509EncodedKeySpec)
           (org.bouncycastle.util.io.pem PemReader)))

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

(defn public-key-from-string [public-key-string]
  (let [pem-reader (PemReader. (StringReader. public-key-string))
        pem-object (.readPemObject pem-reader)
        content (.getContent pem-object)
        spec (X509EncodedKeySpec. content)
        kf (KeyFactory/getInstance "RSA")
        public-key (.generatePublic kf spec)]
    public-key))

(defn base64-encode [bytes]
  (.encodeToString (Base64/getEncoder) bytes))

(defn base64-decode [bytes]
  (.decode (Base64/getDecoder) bytes))

(defn sha256-base64 [data]
  (let [digest (.digest (MessageDigest/getInstance "SHA-256") (.getBytes data))]
    (base64-encode digest)))

(defn sign [data private-key]
  (let [bytes (.getBytes data)
        signer (doto (Signature/getInstance "SHA256withRSA")
                 (.initSign private-key (SecureRandom.))
                 (.update bytes))]
    (.sign signer)))

(defn verify [signature-bytes expected-data public-key]
  (let [expected-bytes (.getBytes expected-data)
        verifier (doto (Signature/getInstance "SHA256withRSA")
                   (.initVerify public-key)
                   (.update expected-bytes))]
    (.verify verifier signature-bytes)))
