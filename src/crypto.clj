(ns crypto
  (:require [clojure.java.io :as io])
  (:import (java.util Base64)
           (java.security MessageDigest)))

(java.security.Security/addProvider (org.bouncycastle.jce.provider.BouncyCastleProvider.))

(defn keydata [reader]
  (->> reader
       (org.bouncycastle.openssl.PEMParser.)
       (.readObject)))

(defn pem-string->pub-key 
  "Convert a PEM-formatted public key string to an RSA public key.
   Returns sun.security.rsa.RSAPublicKeyImpl"
  [string] 
  (let [kd (keydata (io/reader (.getBytes string)))
        kf (java.security.KeyFactory/getInstance "RSA")
        spec (java.security.spec.X509EncodedKeySpec. (.getEncoded kd))]
    (.generatePublic kf spec)))

(defn pem-string->key-pair
  "Convert a PEM-formatted private key string to a public/private keypair.
   Returns java.security.KeyPair."
  [string]
  (let [kd (keydata (io/reader (.getBytes string)))]
    (.getKeyPair (org.bouncycastle.openssl.jcajce.JcaPEMKeyConverter.) kd)))

(defn encode64 [bytes]
  (.encodeToString (Base64/getEncoder) bytes))

(defn sha256-base64 [data]
  (let [digest (.digest (MessageDigest/getInstance "SHA-256") (.getBytes data))]
    (encode64 digest)))

(defn sign
  "RSA private key signing of a message. Takes message as string"
  [message private-key] 
  (let [msg-data (.getBytes message)
        sig (doto (java.security.Signature/getInstance "SHA256withRSA")
              (.initSign private-key (java.security.SecureRandom.))
              (.update msg-data))]
    (.sign sig)))

(def test-public-key-str
  (slurp "keys/public.pem"))

(def test-public-key
  (pem-string->pub-key test-public-key-str))

(def test-private-key
  (-> (slurp "keys/private.pem")
      (pem-string->key-pair)
      (.getPrivate)))


