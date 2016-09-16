(ns cemerick.pomegranate.maven
  (:require-macros [cljs.core.async.macros :refer [go]])
  (:refer-clojure :exclude  [type proxy])
  (:require [cljs.nodejs :as nodejs]
            [cljs.core.async :refer [close! put! chan <! >! take!  pipeline alts! poll!] :as async]
            [clojure.set :as set]
            [clojure.string :as str]))

(def path (nodejs/require "path"))
(def request (nodejs/require "request"))
(def repos {:clojars "https://clojars.org/repo"
            :maven-central "https://repo1.maven.org/maven2"})
(def xml2js (nodejs/require "xml2js"))

(defn create-url-for-depedency [repo {group :group artifact :artifact version :version}]
  (let [g (str/replace group #"\." "/")
        art (str/join "-" [artifact version])
        art-url (str/join "/" [repo g artifact version art])
        ext ["pom" "jar"]]
    (map #(str/join "." [art-url %]) ext)))

(defn create-urls-for-dependency [repo d]
  (map #(create-url-for-depedency % d) repo))

(defn make-request
  [url]
  (let [out (chan)]
    (.get request #js {:url url}
          (fn [error response body]
            (put! out [error response body])))
    out))

(defn make-request2 [cout url]
  ;(println (str "downloading from " url))
  (.get request #js {:url url}
        (fn [error response body]
          (put! cout [error response body])))
  cout)



(defn download-pom-for-dependency [dependency-url]
  (let [ xform (map #(nth % 2))
        c (chan 1 xform)]
    (make-request2 c dependency-url)))

(defn parse-xml [xmlstring]
  "Parses the xml"
  (let [x (chan)]
    (.parseString xml2js xmlstring #(put! x %2))
    (poll! x)))

(defn mvndep->dep [x]
  (let [g (first (:groupId x))
        a (first (:artifactId x))
        v (first (:version x))
        m {:group g :artifact a :version v}]
    m))


(defn create-dep-pipeline [url]
  (let [c (chan 1 (comp
                    (map #(nth % 2))
                    (map parse-xml)
                    (map #(js->clj % :keywordize-keys true))
                    (map #(get-in % [:project :dependencies 0 :dependency]))
                    (map #(map mvndep->dep %))
                    (map #(into #{} %))
                    ))]
    (make-request2 c url)))


(defn extract-dependencies [urls]
 (map create-dep-pipeline urls))


(defn resolve [dep]
  (let [x (chan)]
    (go
      (loop [next dep
             to-do #{} ]
        (when next
        (println "Looking for dependencies for " next)
            (let [
                  urls (create-urls-for-dependency (vals repos) next)
                  deps (first (alts! (extract-dependencies (map first urls))))
                 ]
              (println "Dependecies are " deps)
              (let [new-dep (set/union to-do deps)]
                (recur (first new-dep) (rest new-dep))))))
      (close! x))
    x))


(def test-dep
  {:group "cljs-bach"
   :artifact "cljs-bach"
   :version "0.2.0"})
