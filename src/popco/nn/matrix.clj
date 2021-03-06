;;; This software is copyright 2013, 2014, 2015 by Marshall Abrams, and
;;; is distributed under the Gnu General Public License version 3.0 as
;;; specified in the file LICENSE.

(ns popco.nn.matrix
  (require [clojure.core.matrix :as mx]))
;; matrix utility functions

(defn vec-count
  "Given a core.matrix vector (i.e. a true 1-D structure,
  not a 1xn or nx1 matrix), returns its number of elements."
  [cm-vec]
  (first (mx/shape cm-vec)))

(defn square-from-row
  "Make a symmetric matrix by multiplying a row matrix by itself.
  row-mat must be a *matrix* not a vector, i.e. it must be 2D 
  even though only one row."
  [m]
  (mx/mmul (mx/transpose m) m))

(defn square-from-col
  "Make a symmetric matrix by multiplying a column matrix by itself.
  col-mat must be a *matrix* not a vector, i.e. it must be 2D 
  even though only one column."
  [m]
  (mx/mmul m (mx/transpose m)))

(defn square-from-vec
  "Make a symmetric matrix by multiplying a core.matrix vector
  by itself, first making the vector into a row matrix."
  [v]
  (square-from-row (mx/matrix [v])))


(defn zero-index-val-pairs
  "Return [indices val] pairs for zero vals in matrix m.
  (See docstring for zeros.)"
  [m]
  (filter (comp zero? second) 
          (map vector (mx/index-seq m) (mx/eseq m))))

(defn zeros
  "Gets the zero indices of an array mapped to the values.
  (By Matt Revelle at https://github.com/mikera/core.matrix/issues/102
  Something like this will probably be incorporated into core.matrix
  with the name zero-map.)"
  [m]
  (into {} (zero-index-val-pairs m)))

(defn zero-indices
  [m]
  (map first (zero-index-val-pairs m)))

(defn zero-vals
  [m]
  (map second (zero-index-val-pairs m)))


(defn non-zero-index-val-pairs
  "Return [indices val] pairs for non-zero vals in matrix m.
  (See docstring for non-zeros.)"
  [m]
  (filter (comp not zero? second) 
          (map vector (mx/index-seq m) (mx/eseq m))))

(defn non-zeros
  "Gets the non-zero indices of an array mapped to the values.
  (By Matt Revelle at https://github.com/mikera/core.matrix/issues/102
  Something like this will probably be incorporated into core.matrix
  with the name non-zero-map.)"
  [m]
  (into {} (non-zero-index-val-pairs m)))

(defn non-zero-indices
  [m]
  (map first (non-zero-index-val-pairs m)))

(defn non-zero-vals
  [m]
  (map second (non-zero-index-val-pairs m)))


(defn col1
  [m]
  (first (mx/columns m)))

(defn row1
  [m]
  (first (mx/rows m)))

(defn pm-with-breaks
  [m]
  (mx/pm m)
  (println)
  (flush))

;; EH
(defn pm-with-idxs
  [m]
  (let [width (first (mx/shape m))  ; assume vector or square mat
        idx-vec (mx/matrix (range width))]
    (mx/pm idx-vec)
    (mx/pm m)
    (println)
    (flush)))

(defn print-vec-with-labels
  [id-vec m]
  (doseq [i (range (count 
                     (mx/matrix :persistent-vector m)))]
    (println (mx/mget m i) (id-vec i))))
