(ns popco.nn.propn
  (:use popco.core.lot)
  (:require [popco.nn.core :as nn]
            [utils.general :as ug]
            [clojure.core.matrix :as mx])
  (:import [popco.core.lot Propn]
           [popco.nn.core PropnNet]))


(defn make-propn-net
  [propnseq]
  (let [dim (count propnseq)]
    (nn/map->PropnNet
      (assoc 
        (nn/make-nn-core propnseq)
        :wt-mat (mx/new-matrix dim dim)))))