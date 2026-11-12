package redos_experiment

import matching.Witness
import matching.regexp.RegExp
import matching.regexp.RegExpParser
import matching.transition.Lookahead

object Main extends Wrapper {

  def check(source: String, flags0: String): ujson.Obj = {
    // The parser accepts neither `g` nor `m`.
    val flags = flags0.replace("g", "").replace("m", "")
    val (pattern, options) = RegExpParser.parsePCRE(s"/$source/$flags")
    val (degree, witness, approximated, _) = RegExp.calcTimeComplexity(pattern, options, Some(Lookahead))
    val result = degree match {
      case Some(0) => ujson.Obj("status" -> "safe", "complexity" -> ujson.Obj("type" -> "constant"))
      case Some(1) => ujson.Obj("status" -> "safe", "complexity" -> ujson.Obj("type" -> "linear"))
      case Some(d) => vulnerable(polynomial(d), attack(witness))
      case None    => vulnerable(exponential, attack(witness))
    }
    result("approximated") = approximated
    result
  }

  def attack(witness: Witness[Char]): ujson.Value =
    if (witness.pumps.isEmpty) ujson.Null
    else
      pumps(
        witness.separators.init.map(_.mkString),
        witness.pumps.map(_.mkString),
        witness.separators.last.mkString
      )
}
