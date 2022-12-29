(ns build
  (:require [clojure.tools.build.api :as b]
            [deps-deploy.deps-deploy :as dd]))

(def version (format "0.%s" (b/git-count-revs nil)))

(def libs [{:lib 'clj-activitypub/activitypub
            :src ["activitypub-core/src" "activitypub-ring/src"]
            :dir "clj-activitypub"
            :jar-file (format "target/%s-%s.jar" "clj-activitypub" version)
            :basis (b/create-basis {:project "deps.edn"})}
           {:lib 'clj-activitypub/activitypub-core
            :src ["activitypub-core/src"]
            :dir "clj-activitypub-core"
            :jar-file (format "target/%s-%s.jar" "clj-activitypub-core" version)
            :basis (b/create-basis {:project "./clj_activitypub/activitypub-core/deps.edn"})}
           {:lib 'clj-activitypub/activitypub-ring
            :src ["activitypub-ring/src"]
            :dir "clj-activitypub-ring"
            :jar-file (format "target/%s-%s.jar" "clj-activitypub-ring" version)
            :basis (b/create-basis {:project "./clj_activitypub/activitypub-ring/deps.edn"})}])

(def class-dir "target/classes/")

(defn clean [_]
  (b/delete {:path "target"}))

(defn jar [_]
  (doall
   (for [lib-entry libs]
     (let [{:keys [lib src jar-file basis dir]} lib-entry
           target-dir (str class-dir dir)]
       (b/write-pom {:class-dir target-dir
                     :lib lib
                     :version version
                     :basis basis
                     :src-dirs src})
       (b/copy-dir {:src-dirs src
                    :target-dir target-dir})
       (b/jar {:class-dir target-dir
               :jar-file jar-file})))))

(defn install
  "Create a jar file and install in local Maven repo."
  [_]
  (doall
   (for [lib-entry libs]
     (let [{:keys [lib jar-file basis dir]} lib-entry
           target-dir (str class-dir dir)]
       (b/install {:basis basis;; - required, used for :mvn/local-repo
                   :lib lib ;; - required, lib symbol
;; :classifier ;; - classifier string, if needed
                   :version version ;; - required, string version
                   :jar-file jar-file ;; - required, path to jar file
                   :class-dir target-dir ;;- required, used to find the pom file
                   })))))

(defn deploy [_]
  (doall
   (for [lib-entry libs
         :let [{:keys [lib jar-file]} lib-entry]]
     (dd/deploy {:installer :remote
                 :artifact jar-file
                 :pom-file (b/pom-path {:lib lib :class-dir class-dir})}))))