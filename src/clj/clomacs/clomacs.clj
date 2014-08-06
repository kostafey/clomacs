;;; clomacs.clj --- Simplifies emacs lisp interaction with clojure.

;; Copyright (C) 2013 Kostafey <kostafey@gmail.com>

;; Author: Kostafey <kostafey@gmail.com>
;; URL: https://github.com/kostafey/clomacs
;; Keywords: clojure, interaction
;; Version: 0.0.1

;; This file is not part of GNU Emacs.

;; This program is free software: you can redistribute it and/or modify
;; it under the terms of the GNU General Public License as published by
;; the Free Software Foundation, either version 3 of the License, or
;; (at your option) any later version.

;; This program is distributed in the hope that it will be useful,
;; but WITHOUT ANY WARRANTY; without even the implied warranty of
;; MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
;; GNU General Public License for more details.

;; You should have received a copy of the GNU General Public License
;; along with this program.  If not, see <http://www.gnu.org/licenses/>.

(ns clomacs.clomacs
  (:use [clojure.string :only (join split)])
  (:import (java.io StringWriter File)
           (java.net URL URLClassLoader)
           (java.lang.reflect Method)))

(defn parse-artifact [artifact-name]
  "Parse `artifact-name' to list (`group-id' `artifact-id' `version')
Input format, e.g.:
 [org.clojure/clojure \"1.5.1\"]
Ouptut format, e.g.:
 (\"org.clojure\" \"clojure\" \"1.5.1\")"
  (let [group-and-artifact (split (str (first artifact-name)) #"/")
        group-id (first group-and-artifact)
        artifact-id (if (nil? (second group-and-artifact))
                      (first group-and-artifact)
                      (second group-and-artifact))
        version (second artifact-name)]
    (list group-id artifact-id version)))

(defmacro with-artifact [artifact-name & body]
  "Inject `group-id' `artifact-id' `version' local variables to the `body'
scope."
  `(let [artifact# (parse-artifact ~artifact-name)
         ~(symbol "group-id") (nth artifact# 0)
         ~(symbol "artifact-id") (nth artifact# 1)
         ~(symbol "version") (nth artifact# 2)]
     ~@body))

(defn get-path-tail [path]
  (.getName (File. path)))

(defn get-path-parent [path]
  (.getParent (File. path)))

(defn concat-path [& path-list]
  (let [path-cons (fn [& path-list]
                    (loop [acc (File. (first path-list))
                           pl (rest path-list)]
                      (if (empty? pl)
                        acc
                        (recur (File. acc (first pl)) (rest pl)))
                      ))]
    (.getPath (apply path-cons path-list))))

(def is-windows
  "The value is true if it runs under the os Windows."
  (<= 0 (.indexOf (System/getProperty "os.name") "Windows")))

(def is-linux
  "The value is true if it runs under the os Linux."
  (<= 0 (.indexOf (System/getProperty "os.name") "Linux")))

(defn get-m2-path [artifact-name]
  (with-artifact
    artifact-name
    (let [home (if (= (get-path-tail (System/getenv "HOME")) "Application Data")
                 (get-path-parent (System/getenv "HOME"))
                 (System/getenv "HOME"))
          m2 (concat-path home ".m2" "repository")
          sep (if is-windows "\\\\" "/")]
      (concat-path m2
                   (.replaceAll group-id "\\." sep)
                   artifact-id
                   version "/"))))

(defn get-artifact-file-name [artifact-name extension]
  (with-artifact
    artifact-name
    (str artifact-id "-" version "." extension)))

(defn get-jar-location [artifact-name]
  (str (get-m2-path artifact-name)
       (get-artifact-file-name artifact-name "jar")))

(defn add-to-cp "Since add-classpath is deprecated."
  [#^String jarpath] ; path without "file:///..." prefix.
  (let [#^URL url (.. (File. jarpath) toURI toURL)
        url-ldr-cls (. (URLClassLoader. (into-array URL [])) getClass)
        arr-cls (into-array Class [(. url getClass)])
        arr-obj (into-array Object [url])
        #^Method mthd (. url-ldr-cls getDeclaredMethod "addURL" arr-cls)]
    (doto mthd
      (.setAccessible true)
      (.invoke (ClassLoader/getSystemClassLoader) arr-obj))
    (println (format "Added %s to classpath" jarpath))))

(defn print-cp []
  (doseq [url (seq
               (.getURLs (java.lang.ClassLoader/getSystemClassLoader)))]
    (println (.getFile url))))

(defn load-artifact [artifact-name]
  (add-to-cp (get-jar-location artifact-name)))

(def offline-atom (atom false))

(defn set-offline [is-offline]
  (reset! offline-atom is-offline))

(comment
  (in-ns 'clomacs.clomacs)
  (get-jar-location '[leiningen-core "2.1.3"])
  (load-artifact '[com.cemerick/pomegranate "0.2.0"])

  (print-cp)
  ;; (add-to-cp (.replaceAll (get-jar-location '[org.clojure/clojure-contrib "1.2.0"]) "\\\\" "/"))

  )
