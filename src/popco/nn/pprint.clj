(ns popco.nn.pprint
  (:use [clojure.pprint :only [cl-format]])
  (:require [clojure.core.matrix :as mx]
            [clojure.string :as string])
  (:import [popco.nn.core AnalogyNet]))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; FUNCTIONS FOR DISPLAYING MATRICES, NN-STRUS WITH LABELS

;; TODO: Note that core.matrix/pm does some pretty-printing of matrices.
;;       Consider using it.  Or adding to it.

(defn max-strlen
  "Returns the maximum length of the strings contained in its argument."
  [strings]
  (apply max (map count strings)))

(defn collcolls-to-vecvecs
  "Converts a collection of collections to a vector of vectors."
  [coll]
  (vec (map #(vec %) coll)))

;; Code notes:
;; This function does the following:
;; - Add spaces to beginning of labels so they're all the same length.
;; - Convert each string to a vector of characters, and then use a matrix
;;   transpose operation to produce inner vectors of characters each of which
;;   is from the same position in different label.
;; - Interpose spaces between these characters, so label columns will line up with
;;   numeric matrix columns in the end.
;; - Convert each inner vector to a single string--a row of characters in the output.
;; - Interpose newlines and left padding on each such string, because the matrix output
;;   may be preceded by row labels.
;; - Add the same padding to the first line, without newline, and add a final newline.
;; - Convert the whole outer vector of strings to one large string.
;; (Maybe there's a way to do more of this with cl-format.)
;; NOTE: In cl-format, @ says to insert padding on left, v says to replace
;; the v with the next argument before processing the one after it.
(defn format-top-labels
  "ADD DOCSTRING"
  [labels intercolumn-width left-pad-width sep]
  (let [label-height (max-strlen labels)
        initial-pad (cl-format nil "~v@a~a~v@a" left-pad-width "" sep intercolumn-width "") ; @ causes pad spaces on left, vs default on right
        interline-pad (cl-format nil "~a~%~a" sep initial-pad)
        intercolumn-pad (cl-format nil "~a~va" sep intercolumn-width "")]
    (apply str                                           ; make the whole thing into a big string
           (conj (vec                                    ; add final newline
                      (cons initial-pad                     ; add initial spaces to first line
                            (interpose interline-pad        ; add newlines and initial spaces to each line except the first
                                       (map #(apply str %)  ; concatenate each inner vector to a string
                                            (map #(interpose intercolumn-pad %)  ; add spaces between chars from each column
                                                 (mx/transpose                   ; (cheating by using a numeric matrix op, but convenient)
                                                               (collcolls-to-vecvecs    ; convert to Clojure vector of vector, which will be understood by core.matrix
                                                                                     (map #(cl-format nil "~v@a" label-height %) ; make labels same width (transposed: height)
                                                                                          labels)))))))) 
                 sep
                 "\n"))))

(defn format-mat-with-row-labels
  "ADD DOCSTRING"
  [pv-mat labels nums-width sep]
  (let [num-labels (count labels)
        labels-width (max-strlen labels)
        nums-widths (repeat num-labels (+ 1 nums-width)) ; we'll need a list of repeated instances of nums-width
        seps (repeat num-labels sep)]                    ; and separators
    (map (fn [row label]
           (cl-format nil "~v@a~a ~{~vf~a~}~%" 
                      labels-width label sep
                      (interleave nums-widths row seps))) ; Using v to set width inside iteration directive ~{~} requires repeating the v arg
         pv-mat labels)))

; This is rather slow, but fine if you don't need to run it very often.
(defn format-matrix-with-labels
  "Format a matrix mat with associated row and column labels into a string
  that could be printed prettily.  row-labels and col-labels must be sequences
  of strings in index order, corresponding to indexes from 0 to n.  If a string
  is provided as an additional, optional sep argument, it will be used to 
  separate columns.  For example, you can use a string containing a comma to 
  generate csv output."
  ([mat row-labels col-labels] (format-matrix-with-labels mat row-labels col-labels "")) ; default to empty string as column separator
  ([mat row-labels col-labels sep]
   (let [pv-mat (mx/matrix :persistent-vector mat) ; "coerce" to Clojure vector of Clojure (row) vectors
         nums-width (+ 0 (max-strlen 
                           (map #(cl-format nil "~f" %)   ; REWRITE WITH mx/longest-nums
                                (apply concat pv-mat))))
         left-pad-width (max-strlen row-labels)]
     (apply str
            (concat
              (format-top-labels col-labels nums-width left-pad-width sep)
              (format-mat-with-row-labels pv-mat row-labels nums-width sep))))))

(defn format-nn
  "Format the matrix in nnstru with associated row, col info into a string
  that would be printed prettily.  Display fields are fixed width, so this
  can also be used to output a matrix to a file for use in other programs."
  ([nnstru mat-key] (format-nn mat-key ""))
  ([nnstru mat-key sep]
   (let [labels (map name (map :id (:node-vec nnstru))) ; get ids in index order, convert to strings.  [or: (sort-by val < (:id-to-idx nnstru))]
         mat (get nnstru mat-key)]
     (format-matrix-with-labels mat labels labels sep))))

(defn pprint-nn
  "Pretty-print the matrix in nnstru with associated row, col info."
  [nnstru mat-key]
  (print (format-nn nnstru mat-key)))

(defn dotformat
  "Given a string for display of a matrix (or anything), replaces
  '0.0's with ' . 's."
  [matstring]
  (string/replace matstring #"\b0\.0\b"  " . ")) ; \b matches word border. Dot escaped so only matches dots.

(defn dotformat-nn
  "Format matrix in nnstru with associated row, col info, like
  the output of format-nn, but with '0.0' replaced by dot.
  Display fields are fixed width, so this can also be used to output
  a matrix to a file for use in other programs."
  [nnstru mat-key]
  (dotformat (format-nn nnstru mat-key)))

(defn dotprint-nn
  "Pretty-print the matrix in nnstru with associated row, col info,
  replacing zeros with dots, so that it's easy to distinguish zeros
  from other values."
  [nnstru mat-key]
  (print (dotformat-nn nnstru mat-key)))
