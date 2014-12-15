;;; This software is copyright 2013, 2014, 2015 by Marshall Abrams, and
;;; is distributed under the Gnu General Public License version 3.0 as
;;; specified in the file LICENSE.

;; Utility functions having to do with strings and printing
(ns utils.string
  (:require [clojure.pprint :only [*print-right-margin*]]))

(defn extract-fn-name
  "Given a function, extracts the original function name from Clojure's
  internal string identifier associated with the function, returning this
  name as a string.  Note that the internal string indentifier uses underlines,
  where the original name used dashes, but this function replaces them with 
  dashes to get back the original name.  (If there were underlines in the
  original function name this will replace them with dashes anyway.)"
  [f]
  (clojure.string/replace 
    (clojure.string/replace (str f) #".*\$(.*)@.*" "$1") ; strip off initial and trailing parts of the identifier
    #"_" "-")) ; replace underlines with dashes

(defmacro add-to-docstr
  "Appends string addlstr onto end of existing docstring for symbol sym.
  (Tip: Consider beginning addlstr with \"\\n  \".)"
  [sym addlstr] 
  `(alter-meta! #'~sym update-in [:doc] str ~addlstr))

(defn set-pprint-width 
  "Sets width for pretty-printing with pprint and pp."
  [cols] 
  (alter-var-root 
    #'clojure.pprint/*print-right-margin* 
    (constantly cols)))

(defn println-stderr
  "Like println, but prints to stderr."
  [& more]
  (binding [*out* *err*]
    (apply println more)))

(defn println-and-ret
  "Print a single argument with println, then return that argument.
  Useful for debugging."
  [arg]
  (println arg)
  arg)

(defn add-quotes
  "Append initial and terminal double-quote characters to string."
  [string]
  (str "\"" string "\""))

(defn add-quotes-if-str
  [x]
  (if (string? x)
    (add-quotes x)
    x))

(defn seq-to-csv-row-str
  "Given a sequence, create a string representing a row in csv format, with
  each element in the sequence as an element in the csv row.  Strings will
  be surrounded by quote characters.  Comma is used as the delimiter.  A
  terminating newline is not added."
  [s]
  (apply str 
         (interpose ", " (map add-quotes-if-str s))))

(defn upper-case-keyword
  "Converts a keyword to its uppercase analogue."
  [kw]
  (keyword 
    (clojure.string/upper-case (name kw))))

(defn lower-case-keyword
  "Converts a keyword to its lowercase analogue."
  [kw]
  (keyword 
    (clojure.string/lower-case (name kw))))

(defn erase-chars
  "Erase up to len characters from the console on the current line."
  [len]
  (print (apply str (repeat len \backspace))))

(defn dorun-nl
  "Like dorun, but prints a newline to console before returning."
  [s]
  (dorun s)
  (println))
