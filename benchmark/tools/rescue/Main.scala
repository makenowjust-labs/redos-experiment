package redos_experiment

import cn.edu.nju.moon.redos.attackers.GeneticAttacker
import cn.edu.nju.moon.redos.regex.ReScuePattern

object Main extends Wrapper {

  def check(source: String, flags: String): ujson.Obj = {
    val pattern = ReScuePattern.compile(source)
    val trace = new GeneticAttacker().attack(pattern)
    if (trace != null && trace.attackSuccess())
      ujson.Obj("status" -> "vulnerable", "attack" -> ujson.Obj("string" -> trace.str))
    else safe
  }
}
