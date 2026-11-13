package redos_experiment

import scala.jdk.CollectionConverters.*

import cn.ac.ios.Bean.AttackType
import cn.ac.ios.ReDoSMain

object Main extends Wrapper {

  // The language whose engine validates the attack strings: the image has the engines of `js` and `java`.
  val language: String = sys.env.getOrElse("REDOSHUNTER_LANGUAGE", "js")

  def check(source: String, flags: String): ujson.Obj = {
    val regex = s"/$source/$flags"
    val bean = ReDoSMain.validateReDoS(ReDoSMain.checkReDoS(regex, 0, "11111", language), "s", language)
    bean.getAttackBeanList.asScala.find(_.isAttackSuccess) match {
      case Some(attack) =>
        val complexity = attack.getType match {
          case AttackType.EXPONENT   => exponential
          case AttackType.POLYNOMIAL => ujson.Obj("type" -> "polynomial")
          case _                     => ujson.Null
        }
        val result =
          vulnerable(complexity, pumps(Seq(attack.getPrefix), Seq(attack.getInfix), attack.getSuffix))
        result("pattern") = attack.getPatternType.name
        result("repeat") = attack.getRepeatTimes
        result
      case None => safe
    }
  }
}
