(ns popco.core.communic
  (:require [utils.general :as ug]
            [popco.nn.nets :as nn]
            [popco.nn.analogy :as an]
            [clojure.pprint :as pp] ; TEMPORARY
            [clojure.core.matrix :as mx])) ; for unmask!

;; TODO MOVE ELSEWHERE?
(defn unmask!
  "Given a core.matrix vector representing a mask, and an index
  into the mask, set the indexed element of the mask to 1."
  [mask idx]
    (mx/mset! mask idx 1.0))

;; TODO MOVE ELSEWHERE?
(defn node-unmasked?
  "Given a core.matrix vector representing a mask, and an index
  into the mask, return true if the mask is 1 at that index;
  otherwise false."
  [mask idx]
  (= 1.0 (mx/mget mask idx)))

(declare receive-propn add-to-propn-net add-to-analogy-net propn-already-unmasked?  
         propn-components-already-unmasked?  ids-to-poss-mn-id unmask-mapnode-extended-family!)

(defn receive-propn
  [pers propn]
    (add-to-propn-net pers propn) ; test for already being unmasked? maybe not since add-to-propn-net is cheap
    ; TODO also find propns that the new propn participates in, and try to add them to analogy net as well
    (add-to-analogy-net pers propn)) ; TODO add test for already having mapnode, since add-to-analogy-net is not cheap

(defn add-to-propn-net
  [pers propn]
  (let [pnet (:propn-net pers)]
    (unmask! (:propn-mask pers) ((:id-to-idx pnet) propn))))

(defn add-to-analogy-net
  "ADD DOCSTRING.  See communic.md for further explanation."
  [pers propn]
  (when (propn-components-already-unmasked? pers propn)                ; if sent propn missing extended-family propns, can't match
    (doseq [a-propn ((:propn-to-analogues (:analogy-net pers)) propn)] ; check possible analogue propns to sent propn
      (when (and (propn-already-unmasked? pers a-propn)                ; if pers has this analogue propn
                 (propn-components-already-unmasked? pers a-propn))    ; and its extended-family-propns 
        (let [mn-id (or (ids-to-poss-mn-id pers a-propn propn)         ; then unmask mapnode corresponding to this propn pair
                        (ids-to-poss-mn-id pers propn a-propn))]
          (unmask-mapnode-extended-family! pers mn-id))))))            ; and all extended family mapnodes

(defn propn-already-unmasked?
  "Return true if, in person (first arg), propn (second arg) exists in the
  proposition net in the sense that it has been unmasked; false otherwise."
  [{{id-to-idx :id-to-idx} :propn-net ; bind field of propn-net of person that's passed as 2nd arg
    propn-mask :propn-mask}           ; bind propn-mask of person
   propn]
  (node-unmasked? propn-mask (id-to-idx propn)))

(defn propn-components-already-unmasked?
  "Return true if, in person (first arg), propn (second arg) is a possible
  candidate for matching--i.e. if its component propns (and therefore
  preds, objs) already exist, i.e. have been unmasked.  Returns false if not."
  [{{propn-to-family-propn-idxs :propn-to-family-propn-idxs} :propn-net ; bind field of propn-net of person that's passed as 2nd arg
    propn-mask :propn-mask} ; bind propn-mask of person
   propn]
  (every? (partial node-unmasked? propn-mask) 
          (propn-to-family-propn-idxs propn))) ; if propn is missing extended-family propns, can't match

(defn ids-to-poss-mn-id
  "Given two id keywords and a person, constructs and returns 
  a corresponding mapnode id, or nil if the id has no index."
  [{{id-to-idx :id-to-idx} :analogy-net} ; bind index map from analogy-net in person
   propn1-id propn2-id]
  (an/ids-to-poss-mapnode-id propn1-id propn2-id id-to-idx))

(defn unmask-mapnode-extended-family!
  [{{propn-mn-to-ext-fam-idxs :propn-mn-to-ext-fam-idxs} :analogy-net ; bind index map from analogy-net in person
    analogy-mask :analogy-mask} ; bind mask in person
   mn-id]
  (doseq [idx (propn-mn-to-ext-fam-idxs mn-id)]
    (unmask! analogy-mask idx)))


;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; TEMPORARY DEFS FOR TESTING
(def net-has-node? node-unmasked?)
;; Keeping around as a sanity check of the new version above.
(defn old-add-to-analogy-net
  [pers propn]
  (let [analogy-mask (:analogy-mask pers)
        anet (:analogy-net pers)
        aid-to-idx (:id-to-idx anet)
        aid-to-ext-fam-idxs (:propn-mn-to-ext-fam-idxs anet)
        analogue-propns ((:propn-to-analogues anet) propn)

        propn-mask (:propn-mask pers)
        pnet (:propn-net pers)
        pid-to-idx (:id-to-idx pnet)
        pid-to-propn-idxs (:propn-to-family-propn-idxs pnet) 
        
        propn-net-has-node? (partial net-has-node? propn-mask)
        unmask-mapnode! (partial unmask! analogy-mask) ]

    ;(pp/cl-format true "propn: ~s~%" propn) ; DEBUG
    (when (every? propn-net-has-node? (pid-to-propn-idxs propn)) ; if sent propn missing extended-family propns, can't match
      (doseq [a-propn analogue-propns]                         ; now check any possible matches to sent propn
        ;(pp/cl-format true "\ta-propn: ~s ~s ~s~%" a-propn (pid-to-idx a-propn) (propn-net-has-node? (pid-to-idx a-propn))) ; DEBUG
        ;(pp/cl-format true "\tsub-a-propns propn-net-has-node?: ~s ~s~%" (pid-to-propn-idxs a-propn) (every? propn-net-has-node? (pid-to-propn-idxs a-propn))) ; DEBUG
        (when (and 
                (propn-net-has-node? (pid-to-idx a-propn))                ; pers has this analogue propn
                (every? propn-net-has-node? (pid-to-propn-idxs a-propn))) ; and its extended-family-propns 
          ;; Then we can unmask all mapnodes corresponding to this propn pair:
          (let [aid (or (an/ids-to-poss-mapnode-id a-propn propn aid-to-idx)   ; TODO: replace the or by passing in the analogue-struct?
                        (an/ids-to-poss-mapnode-id propn a-propn aid-to-idx))]
            ;(pp/cl-format true "\t\taid + idxs: ~s~%" aid (aid-to-ext-fam-idxs aid)) ; DEBUG
            (ug/domap unmask-mapnode! (aid-to-ext-fam-idxs aid)))))))) ; unmask propn mapnode, pred mapnode, object mapnodes, recurse into arg propn mapnodes
