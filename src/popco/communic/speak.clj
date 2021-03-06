;;; This software is copyright 2013, 2014, 2015 by Marshall Abrams, and
;;; is distributed under the Gnu General Public License version 3.0 as
;;; specified in the file LICENSE.

(ns popco.communic.speak
  (:require [utils.random :as ran]
            ;[clojure.pprint :as pp] ; only if needed for cl-format
            [popco.core.lot :as lot]
            [popco.nn.nets :as nn]
            [popco.nn.analogy :as an]
            [popco.communic.utterance :as ut]
            [clojure.core.matrix :as mx]))

(declare choose-listeners worth-saying worth-saying-ids choose-propn-ids-to-say make-utterances speaker-plus-utterances update-qualities)

(defn choose-listeners
  "Given a person as argument, return a sequence of persons to whom
  the argument person wants to talk on this tick."
  [{:keys [talk-to-persons max-talk-to rng]}] ; a person
  (if (>= max-talk-to (count talk-to-persons))
    talk-to-persons
    (ran/sample-without-repl rng max-talk-to talk-to-persons)))

(defn worth-saying
  "Function for testing whether a proposition is worth saying given that it is
  otherwise utterable."
  [pers activn]
  (< (ran/next-double (:rng pers)) 
     activn))

;; Note that SALIENT will be filtered out because the usual procedures for creating
;; persons in a population puts only proposition ids in the utterable-ids field
;; of each person.  utterable-mask, used here, is created from utterable-ids.
;; Since SALIENT will not be in utterable-ids, it won't be unmasked in utterable-mask,
;; and therefore won't get put forward as something to say.
(defn worth-saying-ids
  "Given a Person, returns ids of propositions that the person might be willing
  to say to someone at this time.  The set of selected propositions is a subset
  of the intersection of the propositions currently entertained by the person
  (ones not masked in propn-mask) and the propositions that the person is 
  willing to communicate (ones unmasked in utterable-mask).  Each proposition
  in this intersection is then selected with probability equal to the absolute
  value of its activation."
  [{:keys [propn-net utterable-mask rng] :as pers}] ; argument is a Person
  ;; absolute values of activns of unmasked utterable propns:
  (let [propn-mask (:mask propn-net)
        propn-activns (:activns propn-net)
        propn-id-vec (:id-vec propn-net)
        utterable-abs-activns (mx/abs
                                (mx/emul propn-mask utterable-mask propn-activns))]
    (for [i (range (count propn-id-vec))
          :let [abs-activn (mx/mget utterable-abs-activns i)]
          :when (worth-saying pers abs-activn)]
      (propn-id-vec i))))

(defn choose-propn-ids-to-say
  "Given a speaker and a number of utterances, chooses propositions worth
  sayping, and then randomly samples (with replacement) num-utterances of
  those propositions, returning their ids or nil if there are no utterances
  to return."
  [speaker num-utterances]
  (if (pos? num-utterances)
    (if-let [poss-utterance-ids (seq (worth-saying-ids speaker))]  ; since sample throws exception on empty coll
      (ran/sample-with-repl (:rng speaker) num-utterances poss-utterance-ids)
      nil)
    nil))

(defn make-utterances
  "Given a person, returns a Clojure map representing utterances to
  persons, i.e. a map from persons who are listeners--i.e. persons
  who the current person is trying to speak to--to utterances
  to be conveyed from the current person to each of those listeners.
  i.e. there is (at most) one utterance per listener, from speaker,
  in a given timestep.  Utterances are sequences in which the first 
  element represents a proposition via its id, and the second element
  captures the way in which the proposition should influence the
  listener as a function of the proposition's activation in the speaker.
  Each utterance is individually-wrapped in vector to facilitate joining
  the map created by this function with other similar maps, in order to
  create one large map in which the values are sequences of utterances."
  [speaker]
  (let [quality (:quality speaker)
        listeners (choose-listeners speaker) ; may be empty
        to-say-ids (choose-propn-ids-to-say speaker (count listeners))] ; nil if no listeners, possibly nil if so
    (if to-say-ids
      (zipmap listeners 
              (map #(vector (ut/make-utterance speaker % quality)) to-say-ids)) ; wrapping each single utterance
      {})))

;; So person will get passed through
(defn speaker-plus-utterances
  [pers]
  [pers (make-utterances pers)])

(defn update-quality
  [pers]
  (if-let [quality-fn (:quality-fn pers)]
    (assoc pers :quality (quality-fn pers))
    pers)) ; btw quality should normally be nil at this point because it was initalized to have that value, and there's no quality update function.
