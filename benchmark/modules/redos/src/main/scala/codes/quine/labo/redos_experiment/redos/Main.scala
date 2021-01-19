package codes.quine.labo.redos_experiment.redos

import scala.collection.mutable
import scala.concurrent.duration._

import codes.quine.labo.redos.Checker
import codes.quine.labo.redos.Config
import codes.quine.labo.redos.Diagnostics._
import codes.quine.labo.redos.ReDoS
import codes.quine.labo.redos.automaton.Complexity._
import codes.quine.labo.redos.util.Timeout
import codes.quine.labo.redos_experiment.common._

object Main {
  var checker: Checker = Checker.Hybrid

  def config: Config = Config(
    checker = checker,
    timeout = Timeout.from(10.second)
  )

  def main(args: Array[String]): Unit = {
    val inputPath = args(0)
    println(s"==> Input: $inputPath")
    val outputPath = args(1)
    println(s"==> Output: $outputPath")
    checker = args(2) match {
      case "hybrid"    => Checker.Hybrid
      case "automaton" => Checker.Automaton
      case "fuzz"      => Checker.Fuzz
    }
    println(s"==> Checker: $checker")
    println()

    val infoList = IO.read[Seq[RegExpInfo]](inputPath)
    val infoSize = infoList.size
    val counts = mutable.Map(
      Status.Safe -> 0,
      Status.Vulnerable -> 0,
      Status.Timeout -> 0,
      Status.Error -> 0
    )
    val results = infoList.zipWithIndex.map { case (info, i) =>
      println(s"==> [$i/$infoSize] (${counts.map { case (s, c) => s"$s: $c" }.mkString(", ")})")
      val result = test(info)
      counts(result.status) += 1
      result
    }
    println(s"==> [$infoSize/$infoSize] (${counts.map { case (s, c) => s"$s: $c" }.mkString(", ")})")
    IO.write(outputPath, results)
  }

  def test(info: RegExpInfo): Result = {
    println(s"==> Test $info")
    val start = System.nanoTime()
    val diagnostics = ReDoS.check(info.source, info.flags, config)
    val time = System.nanoTime() - start
    val used = diagnostics.used.map(_.toString.toLowerCase)
    println(s"==> Done in ${time / 1e9} s${used.map(x => s" (used: $x)").getOrElse("")}")

    System.gc()
    System.runFinalization()

    diagnostics match {
      case Vulnerable(attack, c, _) =>
        val complexity = c match {
          case Some(Exponential(_))   => Some("exponential")
          case Some(Polynomial(d, _)) => Some(s"$d polynomial")
          case None                   => None
        }
        println(s"==> Result: vulnerable${complexity.map(x => s" (complexity: $x)").getOrElse("")}")
        Result(info, time, Status.Vulnerable, used, Some(attack.toString), complexity, None)
      case Safe(c, _) =>
        val complexity = c match {
          case Some(Constant) => Some("constant")
          case Some(Linear)   => Some("linear")
          case None           => None
        }
        println(s"==> Result: safe${complexity.map(x => s" (complexity: $x)").getOrElse("")}")
        Result(info, time, Status.Safe, used, None, complexity, None)
      case Unknown(ErrorKind.Timeout, _) =>
        println(s"==> Result: timeout")
        Result(info, time, Status.Timeout, used, None, None, None)
      case Unknown(error, _) =>
        println(s"==> Result: error ($error)")
        Result(info, time, Status.Error, used, None, None, Some(error.toString))
    }
  }
}
