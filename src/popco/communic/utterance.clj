;;; This software is copyright 2013, 2014, 2015 by Marshall Abrams, and
;;; is distributed under the Gnu General Public License version 3.0 as
;;; specified in the file LICENSE.

(ns popco.communic.utterance
  (:require [clojure.core.matrix :as mx]
            [utils.general :as ug]))

(defrecord Utterance [propn-id valence speaker-id])

(ug/add-to-docstr ->Utterance
  "Make an utterance to be communicated to a listener.
  :propn-id -   id for propn being communicated.
  :valence -    Effect of utterance on speaker is proportional to this.
                Originally, 1 or -1, but other values in [-1,1] are possible.
  :speaker-id - id of person uttering the proposition.  Has no effect, but
                provides useful information for someone observing the program.")

(defn utterance-valence
  [speaker propn-id]
  (let [pnet (:propn-net speaker)
        id-to-idx (:id-to-idx pnet)
        propn-activns (:activns pnet)]
    (ug/sign-of (mx/mget propn-activns (id-to-idx propn-id)))))
    
(defn make-utterance
  [speaker propn-id]
  (->Utterance propn-id 
               (utterance-valence speaker propn-id)
               (:id speaker)))

