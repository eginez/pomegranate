(ns cemerick.pomegranate.maven-test
  (:require-macros [cljs.core.async.macros :refer [go]])
  (:require [cljs.test :refer-macros [async deftest is testing]]
            [cljs.core.async :refer [put! take! chan <! >!] :as async]
            [cemerick.pomegranate.maven :as maven]))

;com/cognitect/transit-java/0.8.313
(def test-dep
  {:group "com.cognitect"
   :artifact "transit-java"
   :version "0.8.313"})

(def test-url (maven/create-urls-for-dependency (:maven-central maven/repos) test-dep))

(deftest create-url
  (let [urls (maven/create-urls-for-dependency (:local maven/repos) test-dep)]
    (assert (-> urls first coll? not))))

(deftest create-url-repos
  (let [urls (maven/create-urls-for-dependency (vals maven/repos) test-dep)]
    (println (map first urls))
    (assert (-> urls first coll?))))


;(deftest all-repos
;  (println (map first (maven/create-urls-for-dependency maven/repos test-dep))))

(deftest  get-depedency-info
  (async done
     (go
       (let[body (<! (maven/download-pom-for-dependency (first test-url)))]
         ;(println body)
         (assert (-> body nil? not))
         (done)))))


(deftest resolve
  (async done
    (go
      (let [d (<! (maven/resolve test-dep))]
        (println d)
        (done)))))







