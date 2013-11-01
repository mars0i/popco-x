;;; clojure-utils.clj
;;; Utility functions handy for any Clojure program
;;; Marshall Abrams

(ns utils)

(defn set-pprint-width 
  "Sets width for pretty-printing with pprint and pp."
  [cols] 
  (alter-var-root 
    #'clojure.pprint/*print-right-margin* 
    (constantly cols)))

(defmacro add-to-docstr
  "Appends string addlstr onto end of existing docstring for symbol sym.
  (Tip: Consider beginning addlstr with \"\\n\".)"
  [sym addlstr] 
  `(alter-meta! #'~sym update-in [:doc] str ~addlstr))
