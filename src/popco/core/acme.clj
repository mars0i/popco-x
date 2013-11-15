(ns popco.core.acme
  (:use popco.core.lot)
  (:import [popco.core.lot Propn Pred Obj])
  (:require [utils.general :as ug]
            [clojure.core.matrix :as mx])
  (:gen-class))

;; SEE acme.md for an overview of what's going on in this file.

;;; TODO construct link matrix and weight matrix with positive weights
;;; TODO add negative weights to weight matrix
;;; NOTE for the analogy net we probably don't really need the link
;;; matrix, strictly speaking, because there are no zero-weight links.
;;; For the belief network, however, we need to allow zero-weight links,
;;; so a link matrix is needed.

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; STEP 1
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
;; STEP 2
;; For all isomporphic propositions, pair up their components
;; (By separating step 1 and step 2, we do some redundant work, but it makes
;; the logic a bit simpler, for now, and we'll only be doing this once for the
;; whole population at the beginning of each simulation.  By separating step 2
;; and step 3--which merely flattens what we produce here, we preserve structure
;; of relationships between map nodes for use in step 4.)

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

;; NOTE we use sorted-maps here because when we construct mapnode ids,
;; we need it to be the case that (vals clojure-map) always returns these
;; vals in the same order :alog1, :alog2:
;;
(defn match-components-of-propn-pair
  ;; ADD DOCSTRING
  [[p1 p2]]
  ;; return a seq of matched pairs:
  (list    ; that this is a list (not vec) flags that this is a family of map-pairs from the same proposition
    (sorted-map :alog1 p1 :alog2 p2)                 ; we already know the propns match
    (sorted-map :alog1 (:pred p1) :alog2 (:pred p2)) ; predicates always match if the propns matched
    (vec (map match-args (:args p1) (:args p2)))))   ; args match if objs, propns need more work.  vec means these pairs are from two arglists

(defmulti  match-args (fn [x y] [(class x) (class y)]))
(defmethod match-args [Obj Obj] [o1 o2] (sorted-map :alog1 o1 :alog2 o2))
(defmethod match-args [Propn Propn] [p1 p2] (match-components-of-propn-pair [p1 p2]))
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; STEP 3
;; Make a flat sequence of unique pairs.  Define vectors and matrices with it.
;; Also define ids for map nodes.
;; The sequence specifies what the analogy network nodes are, i.e. specifies 
;; the meaning of elements in activation vectors and meaning of matrix rows and cols.

;; NOTE some of these functions will probably be abstracted out into a separate file 
;; and ns later, since they'll be used for the proposition network, too.

(defn id-pair-to-mapnode-id
  "Given a 2-element sequence of id keywords, constructs and returns 
  a corresponding mapnode id."
  [[id1 id2]]
  (keyword 
    (str (name id1) "=" (name id2))))

;; NOTE: According to several remarks on the Internet from 2010 into 2013, (keys x)
;; and (vals x) return keys and vals in the same corresponding order.  Moreover,
;; other remarks say that for sorted-maps, (vals x) always returns values according
;; to the sort-order of keys.  So if pairmap is a sorted map, the ids should always
;; come out in the order of :alog1, :alog2.  This is important, because otherwise
;; the mapnode ids we construct from these pairs might arbitrarily swap the parts of
;; the name on either side of "="; thus id's of map nodes would be unstable.  
;; This point about order seems not to be guaranteed by any explicit documentation,
;; as of 10/2013, but the sentiment on the net seems to be that it's reasonable to
;; assume that this behavior won't change.  The most thorough and authoritative 
;; statement that I've found so far (11/2013) about order of (vals x) for sorted-maps
;; is here: https://groups.google.com/d/msg/clojure/2AyndHfeigk/zaD9T5mT6WkJ 

(defn pair-map-to-id-pair
  "Given a map containing two LOT items, returns a 2-element sequence of their ids,
  in order of keys, i.e. in :alog1, :alog2 order if the map is a sorted-map."
  [pairmap]
  (map :id (vals pairmap))) ; See note above about order of vals.

(def pair-map-to-mapnode-id
  (comp id-pair-to-mapnode-id pair-map-to-id-pair))
(ug/add-to-docstr pair-map-to-mapnode-id
  "Given a map containing two LOT items, constructs and returns a corresponding
  mapnode id.")

(defn add-mapnode-id-to-pair-map
  "Given a map containing two LOT items, adds an id field with a mapnode id."
  [pairmap]
  (assoc pairmap :id (pair-map-to-mapnode-id pairmap)))

;; The use of flatten in this function depends on the fact that (a) map-pairs 
;; are not sequences, and (b) all larger groupings of data are sequences.
(defn make-acme-node-vec
  "Given a tree of node info entries (e.g. Propns, pairs of Propns or 
  Objs, etc.), returns a Clojure vector of unique node info entries 
  allowing indexing particular node info entries.  This node vector is
  typically shared by all members of a population; it merely provides
  information about nodes that a person might have.."
  [node-tree]
  (vec 
    (map add-mapnode-id-to-pair-map
         (distinct (flatten node-tree)))))

;; MOVE TO SEPARATE FILE/NS
(defn make-index-map
  "Given a sequence of things, returns a map from things to indexes.  
  Allows reverse lookup of indexes from the things."
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
;; STEP 4
;; Make weight matrix representing link weights

(defn remove-empty-seqs
  [coll]
  (filter #(not (and (seq? %) (empty? %)))  ; can't use seq: needs to work with non-seqs, too
          coll))

;; TODO: THIS IS SURELY WRONG.  (And nasty, regardless.)
;; Also, we really only need :ids in the result.
(defn raise-propn-families
  "Return a seq of all propn-families in pair-tree."
  [pair-tree]
  (letfn [(f [out in]
            (let [out- (remove-empty-seqs out) ; remove-empty-seqs is just papering over a problem? shouldn't be needed?
                  thisone (first in)
                  therest (rest in)]
              (if (empty? in)
                out-
                (cond (seq? thisone) (f (concat (conj out- thisone) (map (partial f ()) thisone))
                                        therest)
                      (propn? thisone) (f (concat out- 
                                                  (mapcat (partial f ()) 
                                                          (:args thisone))) 
                                          therest)
                      (map? thisone) (f (concat (f () (:alog1 thisone))
                                                (f () (:alog2 thisone))
                                                out-) 
                                        therest)
                      :else (f out- therest)))))]
    (f () (vec pair-tree))))


;; MOVE TO SEPARATE FILE/NS
(defn make-wt-mat
  "Returns a core.matrix square matrix with dimension dim, filled with zeros."
  [dim]
  (mx/new-matrix dim dim))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; ALL STEPS - put it all together
;; ...

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
  [node-seq]
  {:nodes (vec node-seq)
   :indexes (make-index-map (map :id node-seq))
   :wt-mat (make-wt-mat (count node-seq))})

(defn make-acme-nn-strus
  ;; ADD DOCSTRING
  [pset1 pset2]
  (let [pair-tree (match-propn-components (match-propns pset1 pset2))
        node-vec (make-acme-node-vec pair-tree)]
    (make-nn-strus node-vec)))

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
