;;; This software is copyright 2013, 2014, 2015 by Marshall Abrams, and
;;; is distributed under the Gnu General Public License version 3.0 as
;;; specified in the file LICENSE.

(ns popco.io.gexf-static
  (:require [clojure.data.xml :as x]
            [clojure.core.matrix :as mx]
            [popco.core.population :as popn]
            [popco.nn.nets :as nn]
            [popco.nn.matrix :as px]
            [utils.general :as ug]))

; The xml declaration will be generated by emit and its cousins. i.e. <?xml version=\"1.0\" encoding=\"UTF-8\"?>")

;; IMPORTANT: During import into Gephi, uncheck "auto-scale".  Otherwise it does funny things with node sizes.

;; Note another way to organize unmasked-non-zero-links below would be with multiple :when clauses:
;; Do the :when test on the mask for i and j, and then the :let,
;; to store the wt, and then a separate :when test on wt.  Yes--
;; you can do that, and the clauses are executed in order; the :let
;; won't be executed if the first :when doesn't succeed.


(def node-size 25)  ; GEXF size
(def edge-weight 10) ; i.e. GEXF weight property, = thickness/weight for e.g. Gephi

;; Generate unique GEXF id numbers for nodes and edges.
;; This will be more convenient than label-based ids for incorporating multiple persons into one graph.
;; It also allows complete replacement of nodes from one tick to the next in dynamic graphs; that's
;; not desirable for Gephi, but might be useful for some other program.
;; (Note: If we parallelize generation of gexf files, this could conceivably create a little bottleneck.  Seems unlikely, though.)
(def node-id-num (atom 0)) ; generate unique node ids for gexf/gephi
(def edge-id-num (atom 0)) ; generate unique edge ids for gexf/gephi
(def popco-to-gexf-node-id (atom {})) ; store relationship between popco ids and gexf node ids so I can look them up to provide source/target ids for edges

(defn gexf-graph
  "Generate a GEXF specification suitable for reading by clojure.data.xml
  functions such as `emit` and `indent-str`.  nodes is a sequence (not vector)
  of clojure.data.xml specifications for GEXF nodes, which can be generated by
  popco.io.gexf/node.  edges is the same sort of thing for edge specifications,
  which can be generated by popco.io.gexf/edges.  mode, if present, should be
  one of the keywords :static (default) or :dynamic, which determine the GEXF 
  graph mode.  Dynamic graphs allow time indexing.  first-tick is ignored
  if mode is :static."
  [nodes edges mode first-tick]
  (x/sexp-as-element [:gexf {:xmlns "http://www.gexf.net/1.2draft"
                             :xmlns:viz "http://www.gexf.net/1.1draft/viz"
                             :xmlns:xsi "http://www.w3.org/2001/XMLSchema-instance"
                             :xsi:schemaLocation "http://www.gexf.net/1.2draft http://www.gexf.net/1.2draft/gexf.xsd"
                             :version "1.2"}
                      [:graph 
                       (cond (= mode :static) {:defaultedgetype "undirected" :mode "static"} 
                             (= mode :dynamic) {:defaultedgetype "undirected" :mode "dynamic" :timeformat "integer" :start (str first-tick)}  ; TODO Is that the correct timeformat??
                             :else (throw (Exception. (str "Bad GEXF graph mode: " mode))))
                       [:attributes {:class "node"}
                        [:attribute {:id "popco-activn" :title "popco-activn" :type "float"}
                         [:default {} "0.0"]]]
                       [:attributes {:class "edge"}
                        [:attribute {:id "popco-wt" :title "popco-wt" :type "float"}
                         [:default {} "0.0"]]]
                       [:nodes {:count (count nodes)} nodes]
                       [:edges {:count (count edges)} edges]]]))

(defn node
  "id should be a string. It will also be used as label. 
  activn is a POPCO activation value."
  [id activn & size-s] ; size-s is a hack so that you can special-case size for some nodes
  (swap! popco-to-gexf-node-id 
         ug/assoc-if-new-throw-if-old id (swap! node-id-num inc)) ; we should never encounter the same id twice.
  (let [size (or (first size-s) node-size)]
    [:node {:id (str @node-id-num) :label id} 
     [:attvalues {} [:attvalue {:for "popco-activn" :value (str activn)}]]
     [:viz:position {:x (str (- (rand 1000) 500)) :y (str (- (rand 1000) 500)) :z "0.0"}] ; doesn't matter for Gephi, but can be useful for other programs to provide a starting position
     [:viz:size {:value (str size)}]]))

(defn nn-to-nodes
  "Given an PropnNet or AnalogyNet, return a seq of node specifications,
  one for each unmasked node, to pass to gexf-graph."
  [nnstru]
  (let [activns (:activns nnstru)
        node-vec (:node-vec nnstru)
        key-to-node (fn [k]
                      (let [[idx] k]                      ; keys from non-zeros are vectors of length 1
                        (node (name (:id (node-vec idx))) ; node-vec is a Clojure vector of Propns
                              (mx/mget activns idx))))]   ; activns is a core.matrix vector of numbers
    (map key-to-node 
         (px/non-zero-indices (:mask nnstru)))))

(defn edge
  "node1-id and node2-id are strings that correspond to id's passed to the
  function node.  popco-wt should be a POPCO link weight.  It will determine
  edge thickness via the GEXF weight attribute via function popco-to-gexf-wt,
  but will also be stored as the value of attribute popco-wt."
  [node1-id node2-id popco-wt]
  [:edge {:id (str (swap! edge-id-num inc))
          :source (str (get @popco-to-gexf-node-id node1-id))
          :target (str (get @popco-to-gexf-node-id node2-id))
          :weight edge-weight}
   [:attvalues {} [:attvalue {:for "popco-wt" :value (str popco-wt)}]]])


(defn unmasked-non-zero-links
  "Returns a sequence of triplets containing indexes and wts from nnstru's wt-mat
  whenever wt is nonzero and is between unmasked nodes.  Doesn't distinguish
  between directed and undirected links, and assumes that all links can be
  found in the lower triangle (including diagonal) of wt-mat."
  [nnstru]
  (let [wt-mat (nn/wt-mat nnstru)
        mask (:mask nnstru)
        size (first (mx/shape mask))]
    (for [i (range size)
          j (range (inc i)) ; iterate through lower triangle including diagonal
          :let [wt (mx/mget wt-mat i j)]
          :when (and (not= 0.0 wt)
                     (pos? (mx/mget mask i))    ; mask values are never negative
                     (pos? (mx/mget mask j)))]  ;  (and almost always = 1)
      [i j wt])))

(defn nn-to-edges
  "Given an PropnNet or AnalogyNet, return a seq of edge specifications,
  one for each edge between unmasked nodes, to pass to gexf-graph.  Doesn't
  distinguish between one-way and two-way links, and assumes that the only
  one-way links are from the feeder node."
  [nnstru]
  (let [node-vec (:node-vec nnstru)
        link-to-edge (fn [[idx1 idx2 wt]]
                       (edge (name (:id (node-vec idx1))) ; node-vec is a Clojure vector of Propns
                             (name (:id (node-vec idx2))) ; node-vec is a Clojure vector of Propns
                             wt))]
    (map link-to-edge (unmasked-non-zero-links nnstru))))

;; Nodes to trick Gephi into thinking that limits of values are more extreme than they actually are
;(def gephi-dummy-nodes [(node "dummy1" -1.0 1.0) (node "dummy2" 0.0 1.0) (node "dummy3" 1.0 1.0)]) ; make sure that Gephi ranking has limits -1, 1 for activns.  third arg is node size override
;(def gephi-dummy-edges [(edge "dummy1" "dummy2" 1.0) (edge "dummy2" "dummy3" -1.0)])  ; make sure that Gephi ranking has limits -1, 1 for link weights

(defn nn-to-gephi-graph
  "Returns a GEXF specification for a graph based on nnstru."
  [nnstru]
  (gexf-graph (nn-to-nodes nnstru)
              (nn-to-edges nnstru)
              :static ; TODO temp kludge
              0)) ; TODO temp kludge


;; NEW STRATEGY FOR DYNAMIC GRAPHS:
;;
;; Given a seq of popns, make a seq of persons at ticks, or rather nnstrus at ticks.
;; Run through the nnstrus:
;;
;; If a node appears for the first time at t1, it will have start: t1, and no end:.
;;      i.e. I need a hashmap of existing nodes in the graph so I can check for their existence
;;      Wait a minute.  Don't I already have that in the nnstru?  
;;      Something that does the same job?   i.e. just check the mask.
;;      uh, well, actually, what you'd need is the mask from the previous tick in order
;;      to know whether the node is new.  (cf. in popco1 where I needed to know new nodes
;;      for GUESS).
;; If an edge appears for the first time at t1, it will have start: t1, and no end:.
;;      i.e. I need a hashmap of existing edges in the graph so I can check for their existence
;;      (Again, I could use the previous nnstru to check for the change.)
;;
;; so either collect a new record of what was there in the past
;; or store structures from the previous nnstru
;;
;; If I'm mapping this through the populations-at-ticks, then I need to preserve that
;; what's getting passed on is a population, and not a population plus something else.
;; However, I can store extra data in the population.  e.g. a special hashmap, or 
;; last year's model of the mask and wt-mat.
;;
;; For each t:
;; Each node or edge will have an activn or popco-wt attribute with start: t and endopen: t+1.
;; For each activn and popco-wt, also assign attributes for Gephi weight, and viz:
;; properties, also with start: t and endopen: t+1.
;; This is done with attvalues and attvalue.
;; 
;; Attributes will have to be declared as dynamic in the header.
;;
;; Question: Can I give the Gephi weight and the viz: attributes timestamps??
;; (If not, then I'm not sure there's a point to bring a dynamic graph into Gephi.
;; Maybe in d3.)
;; p. 18 of the GEXF manual says:
;;
;;      About the weight: dynamic weight can be used with the reserved title "weight"
;;      in attributes. In dynamic mode, the static XML-attribute weight should be ig-
;;      nored if the dynamic one is provided.


(defn net-with-tick
  [person-id net-key popn]
  (assoc (net-key (popn/get-person person-id popn))
         :tick (:tick popn)))

(defn analogy-net-with-tick
  [person-id popn]
  (net-with-tick person-id :analogy-net popn))

(defn propn-net-with-tick
  [person-id popn]
  (net-with-tick person-id :propn-net popn))

