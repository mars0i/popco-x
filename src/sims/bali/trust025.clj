;;; This software is copyright 2013, 2014, 2015 by Marshall Abrams, and
;;; is distributed under the Gnu General Public License version 3.0 as
;;; specified in the file LICENSE.

;; Redefine popco.core.constants/+trust+ to 0.025 (vs 0.05).
(ns sims.bali.trust025)

(in-ns 'popco.core.constants)
(def +trust+ 0.025)
(in-ns 'sims.bali.trust025)
