(ns cemerick.pomegranate-runner
  (:require [doo.runner :refer-macros [doo-tests]]
            [cemerick.pomegranate.maven-test]))

(doo-tests 'cemerick.pomegranate.maven-test)
