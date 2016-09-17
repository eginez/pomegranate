(ns cemerick.pomegranate.maven
  (:require-macros [cljs.core.async.macros :refer [go]])
  (:refer-clojure :exclude  [type proxy])
  (:require [cljs.nodejs :as nodejs]
            [cljs.core.async :refer [close! put! chan <! >! take!  pipeline alts! poll!] :as async]
            [clojure.set :as set]
            [clojure.string :as str]))

(def path (nodejs/require "path"))
(def fs (nodejs/require "fs"))
(def request (nodejs/require "request"))
(def repos {:clojars "https://clojars.org/repo"
            :local "/Users/eginez/.m2/repository"
            :maven-central "https://repo1.maven.org/maven2"})

(def xml2js (nodejs/require "xml2js"))

(defn is-url-local? [url]
  (not (str/starts-with? url "http")))

(defn create-remote-url-for-depedency [repo {group :group artifact :artifact version :version}]
  (let [sep (if (is-url-local? repo) "/" (.-sep path))
        g (str/replace group #"\." sep)
        art (str/join "-" [artifact version])
        art-url (str/join sep [repo g artifact version art])
        ext ["pom" "jar"]]
    (map #(str/join "." [art-url %]) ext)))


(defn create-urls-for-dependency [repos d]
  (if (coll? repos)
    (map #(create-remote-url-for-depedency % d) repos)
    (create-remote-url-for-depedency repos d)))



(defn make-request2 [cout url]
  (.get request #js {:url url}
        (fn [error response body]
          (when-not error
            (println (str "Downloaded from " url))
            (put! cout [error response body]))))
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




(defn create-remote-pipeline [url]
  (let [c (chan 1 (comp
                    (map #(nth % 2))
                    (map parse-xml)
                    (map #(js->clj % :keywordize-keys true))
                    (map #(get-in % [:project :dependencies 0 :dependency]))
                    (map #(map mvndep->dep %))
                    (map #(into #{} %))
                    ))]
    (make-request2 c url)))


(defn create-local-pipeline [fpath]
  (let [c (chan 1 (comp
                    (map parse-xml)
                    (map #(js->clj % :keywordize-keys true))
                    (map #(get-in % [:project :dependencies 0 :dependency]))
                    (map #(map mvndep->dep %))
                    (map #(into #{} %))
                    ))]
    (.readFile fs fpath "utf-8"
               (fn [err data] (when-not err
                                (println "Read file " fpath)
                                (put! c data))))
    c))




(defn create-pipeline [url]
  (if (is-url-local? url)
    (create-local-pipeline url)
    (create-remote-pipeline url)))



(defn extract-dependencies [urls]
 (map create-pipeline urls))


(defn resolve [dep]
  (let [x (chan)]
    (go
      (loop [next dep
             to-do #{}
             done #{} ]
        (if next
          (do
            (println "Looking for dependencies for " next)
            (let [ url-set (create-urls-for-dependency (vals repos) next)
                  urls (map first url-set)
                  deps (first (alts! (extract-dependencies urls)))
                  not-done (set/difference deps done)
                  new-dep (set/union to-do not-done) ;the difference between deps and done not deps by itself
                  done (conj done next) ]
                (recur (first new-dep) (rest new-dep) done)))
          (put! x done)))
      (close! x))
    x))


(def test-dep
  {:group "cljs-bach"
   :artifact "cljs-bach"
   :version "0.2.0"})
