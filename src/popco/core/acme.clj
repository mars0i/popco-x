(ns popco.core.acme
  (:use popco.core.lot)
  (:import [popco.core.lot Propn Pred Obj])
  (:require [utils.general :as ug]
            [clojure.core.matrix :as mx])
  (:gen-class))

;; ACME: an implementation of Holyoak & Thagard's (1989) ACME method
;; of constructing a constraint satisfaction network from two sets
;; of simple representations of entertained propositions.

;;; TODO construct link matrix and weight matrix with positive weights
;;; TODO add negative weights to weight matrix
;;; NOTE for the analogy net we probably don't really need the link
;;; matrix, strictly speaking, because there are no zero-weight links.
;;; For the belief network, however, we need to allow zero-weight links,
;;; so a link matrix is needed.

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; First step: 
;; Find out which propositions can be paired up, i.e. the isomorphic ones.

(declare propns-match? args-match?)

;; Note that order within pairs matters:  It preserves the distinction
;; between the two analogue structures.
(defn match-propns
  "Returns a (lazy) sequence of 2-element sequences, each containing two 
  propositions that match according to propns-match?.  These are propositions 
  that are isomorphic and can be used to construct map nodes."
  [pset1 pset2]
  (for [p1 pset1
        p2 pset2
        :when (propns-match? p1 p2)]
    `(~p1 ~p2)))

;; similar to deep-isomorphic-arglists in popco 1
(defn propns-match?
  "Tests whether two propositions are isomorphic in the ACME sense.
  Recursively checks propositions given as arguments of propositions.
  Returns true if they're isomorphic, false otherwise."
  [p1 p2]
  (let [args1 (:args p1)   ; predicates always match, so we only check args
        args2 (:args p2)]
  (and (= (count args1) (count args2))
       (every? identity (map args-match? args1 args2)))))

;; similar to isomorphic-args in popco 1
(defmulti  args-match? (fn [x y] [(class x) (class y)]) )
(defmethod args-match? [Obj Propn] [_ _] false)
(defmethod args-match? [Propn Obj] [_ _] false)
(defmethod args-match? [Obj Obj] [_ _] true)   ; objects always match
(defmethod args-match? [Propn Propn] [p1 p2] (propns-match? p1 p2))


;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; Second step: 
;; For all isomporphic propositions, pair up their components
;; (By separating step 1 and step 2, we do some redundant work, but it makes
;; the logic a bit simpler, for now, and we'll only be doing this once for the
;; whole population at the beginning of each simulation.)

;; i.e. we feed the return value of match-propns, above, to 
;; match-propn-components-too, below.

(declare match-args match-components-of-propn-pair match-propn-components)

;; Note that order within pairs matters.  It preserves the distinction
;; between the two analogue structures, and allows predicates and objects
;; to have the same names in different analogue structures (sets don't allow that).
(defn match-propn-components
  "Returns a (lazy) sequence of sequences (families) of mapped-pairs of matched
  Propns, Preds, or Objs from a sequence of of pairs of Propns.  Each pair is a
  map with keys :alog1 and :alog2 (analog 1 & 2).  The resulting pairs
  represent the 'sides' of map nodes.  Each subsequence contains the pairs from
  one proposition.  Each Propn family sequence consists of a Clojure map
  representing a pair of Propns, a clojure map representing a pair of Preds,
  and a vector containing representations of paired arguments.  The contents of
  this vector are Clojure maps where the corresponding arguments are Objs, and
  family sequences where the corresponding args are Propns.  i.e. Propns'
  arguments are embedded in a vector so you can tell whether you're looking at
  a collection of pairs from two Propns or pairs from arguments by testing with
  seq? and vec?."
  [pairs]
  (map match-components-of-propn-pair pairs))

(defn match-components-of-propn-pair
  [[p1 p2]]
  ;; return a seq of matched pairs:
  (list
    {:alog1 p1 :alog2 p2}                 ; we already know the propns match
    {:alog1 (:pred p1) :alog2 (:pred p2)} ; predicates always match if the propns matched
    (vec (map match-args (:args p1) (:args p2))))) ; args match if objs, propns need more work

(defmulti  match-args (fn [x y] [(class x) (class y)]))
(defmethod match-args [Obj Obj] [o1 o2] {:alog1 o1 :alog2 o2})
(defmethod match-args [Propn Propn] [p1 p2] (match-components-of-propn-pair [p1 p2]))
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; Third step: 
;; Make a flat sequence of unique pairs.
;; This specifies nodes, and the meaning of elements in activation vectors
;; and of rows and columns of matrices.  Define vectors and matrices.

;; NOTE these functions will probably be abstracted out into a separate file 
;; and ns later, since they'll be used for the proposition network, too.

(defn id-pair-to-mapnode-id
  [[id1 id2]]
  (keyword 
    (str (name id1) "=" (name id2))))

(defn pair-map-to-id-pair
  [pairmap]
  (map :id (vals pairmap)))

(def pair-map-to-mapnode-id 
  (comp id-pair-to-mapnode-id pair-map-to-id-pair))

(defn add-mapnode-id-to-pair-map
  [pairmap]
  (assoc pairmap :id (pair-map-to-mapnode-id pairmap)))

(defn make-acme-node-vec
  "Given a tree of node info entries (e.g. Propns, pairs of Propns or 
  Objs, etc.), returns a Clojure vector of unique node info entries 
  allowing indexing particular node info entries.  This node vector is
  typically shared by all members of a population; it merely provides
  information about nodes that a person might have.."
  [node-tree]
  (vec 
    (map add-acme-id-to-pair-map
         (distinct (flatten node-tree)))))

;; MOVE TO SEPARATE FILE/NS
(defn make-index-map
  "Given a sequence of ids of items (e.g. Propns, pairs of Propns or 
  Objs, etc.), returns a map from ids to indexes.  Allows reverse
  lookup of the nodes indexes.  This map is typically shared by
  all members of a population; it merely provides information about
  nodes that a person might have."
  [ids]
  (zipmap ids (range (count ids))))

;; MOVE TO SEPARATE FILE/NS
(defn make-activn-vec
  "Returns a core.matrix vector of length len, filled with zeros,
  to represent activation values of nodes.  Each person has its own 
  activation vector."
  [len]
  (mx/new-vector len))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; Fourth step: 
;; Make weight matrix representing link weights

;; MOVE TO SEPARATE FILE/NS
(defn make-wt-mat
  "Returns a core.matrix square matrix with dimension dim, filled with zeros."
  [dim]
  (mx/new-matrix dim dim))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; Put it all together
;; ...

(defn mapnode-pair-maps-to-mapnode-ids
  [pairmaps]
  (map id-pair-to-mapnode-id   ; turn id pairs into mapnode ids
       (map (partial map :id)      ; make id pairs from LOT-item pairs
            (map vals pairmaps)))) ; rets pairs of LOT-items (Propns, etc.)

;; MOVE TO SEPARATE FILE/NS
(defn make-nn-strus
  "Given a sequence of data on individual nodes, returns a clojure map with 
  three entries:
  :nodes -   A Clojure vector of data providing information about the meaning
             of particular neural net nodes.  The indexes of the data items
             correspond to indexes into activation vectors and rows/columns
             of weight matrices.  This vector may be identical to the sequence
             of nodes passed in.
  :indexes - A Clojure map from ids of the same data items to integers, 
             allowing lookup of a node's index from its id.
  :wt-mat -  A core.matrix square matrix with dimensions equal to the number of
             nodes, with all elements initialized to 0.0."
  [node-seq ids]
  {:nodes (vec node-seq)
   :indexes (make-index-map ids)
   :wt-mat (make-wt-mat (count node-seq))})

(defn make-acme-nn-strus
  ;; ADD DOCSTRING
  [pset1 pset2]
  (let [pair-tree (match-propn-components (match-propns pset1 pset2))
        node-vec (make-acme-node-vec pair-tree)]
    (make-nn-strus 
      node-vec
      (mapnode-pair-maps-to-mapnode-ids node-vec))))

;; NOW REARRANGE THE PRECEDING OR ADD TO IT TO USE THE TREE RETURNED
;; BY match-propn-components TO CONSTRUCT POSITIVE WEIGHTS AND FILL
;; THE MATRIX.  THEN AN UN-distinct-ED NODE SEQ TO CONSTRUCT NEGATIVE
;; WEIGHTS AND FILL THOSE INTO THE MATRIX.  See acme.nt4 for more.

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;; UTILITIES FOR DISPLAYING DATA STRUCTURES DEFINED ABOVE

(declare fmt-pair-map-families fmt-pair-maps fmt-pair-map)

(defn fmt-pair-map-families
  "Format a sequence of pair-map families into a tree of pairs of :id's."
  [fams]
  (map fmt-pair-maps fams))

(defn fmt-pair-maps
  "Format a sequence of pair-maps into a tree of pairs of :id's.
  The sequence might represent the family of pair-maps from a proposition,
  or it might represent the vector of mapped arguments of the proposition."
  [pairs] ; could be family, or could be mapped args
  (map fmt-pair-map pairs))

(defn fmt-pair-map
  "Format a pair-map represented by a Clojure map, or a collection of them.
  Pair-maps are displayed as 1-element Clojure maps.  Propn-families of
  pair-maps are displayed as sequences, and argument lists are displayed as
  vectors.  (Note: Representing pair-maps as Clojure maps from one item to the
  other has no meaning; t's just a convenient way to get curly braces rather
  than parens or square braces.)"
  [pair-or-pairs]
  (cond (seq? pair-or-pairs)         (fmt-pair-maps pair-or-pairs) ; it's a family of pairs from one Propn
        (vector? pair-or-pairs) (vec (fmt-pair-maps pair-or-pairs)) ; it's an arglist--return in vec to flag that
        :else (array-map (:id (:alog1 pair-or-pairs)) (:id (:alog2 pair-or-pairs))))) ; it's a pair

;; Handy for displaying output of match-propns:
(defn pair-ids
  "Return sequence of pairs of :id fields of objects from sequence prs of pairs."
  [prs]
  (sort  ; does the right thing with pairs of keywords
    (map (fn [[p1 p2]] 
           [(:id p1) (:id p2)])
         prs)))

