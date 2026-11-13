/**
 * @name Backtracking of regular expressions
 * @description The findings of `js/redos` and the terms that `js/polynomial-redos` looks for, without the data flow.
 * @kind table
 * @id redos-experiment/redos
 */

import javascript
private import semmle.javascript.security.regexp.RegExpTreeView::RegExpTreeView as TreeView
private import codeql.regex.nfa.ExponentialBackTracking as ExponentialBackTracking
private import codeql.regex.nfa.SuperlinearBackTracking as SuperlinearBackTracking

module Exponential = ExponentialBackTracking::Make<TreeView>;

module Superlinear = SuperlinearBackTracking::Make<TreeView>;

string sourceFile(Locatable element) {
  exists(File file | file = element.getFile() |
    exists(file.getRelativePath()) and result = file.getBaseName()
  )
}

/**
 * Holds if `message` is `"starting with '" + prefix + "' and "`, or both are empty.
 */
bindingset[message]
predicate prefixOf(string message, string prefix) {
  message = "" and prefix = ""
  or
  message != "" and prefix = message.substring(15, message.length() - 6)
}

predicate finding(RegExpTerm term, string type, string prefix, string pump, string message) {
  exists(string prefixMessage | prefixOf(prefixMessage, prefix) |
    type = "exponential" and
    Exponential::hasReDoSResult(term, pump, _, prefixMessage) and
    // This is the message of `js/redos`.
    message =
      "This part of the regular expression may cause exponential backtracking on strings " +
        prefixMessage + "containing many repetitions of '" + pump + "'."
    or
    type = "polynomial" and
    exists(Superlinear::PolynomialBackTrackingTerm polynomial | polynomial = term |
      pump = polynomial.getPumpString() and
      prefixMessage = polynomial.getPrefixMessage() and
      message = polynomial.getReason()
    )
  )
}

query predicate roots(string file, boolean relevant) {
  exists(RegExpTerm root | root.isRootTerm() and file = sourceFile(root) |
    if exists(Exponential::RegExpRoot r | r.getRootTerm() = root and r.isRelevant())
    then relevant = true
    else relevant = false
  )
}

query predicate fileErrors(string file, string message) {
  exists(JSParseError error | file = sourceFile(error) and message = error.getMessage())
}

query predicate patternErrors(string file, int column, string message) {
  exists(RegExpParseError error |
    file = sourceFile(error) and
    column = error.getLocation().getStartColumn() and
    message = error.getMessage()
  )
}

query predicate findings(
  string file, string type, int startColumn, int endColumn, string term, string prefix, string pump,
  string message
) {
  exists(RegExpTerm t |
    finding(t, type, prefix, pump, message) and
    file = sourceFile(t) and
    t.getLocation().hasLocationInfo(_, _, startColumn, _, endColumn) and
    term = t.toString()
  )
}
