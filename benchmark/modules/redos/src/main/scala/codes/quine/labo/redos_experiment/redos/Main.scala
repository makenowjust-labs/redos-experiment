package codes.quine.labo.redos_experiment.redos

import scala.collection.mutable
import scala.concurrent.duration._

import codes.quine.labo.redos.Checker
import codes.quine.labo.redos.Config
import codes.quine.labo.redos.Diagnostics._
import codes.quine.labo.redos.ReDoS
import codes.quine.labo.redos.automaton.Complexity.Constant
import codes.quine.labo.redos.automaton.Complexity.Exponential
import codes.quine.labo.redos.automaton.Complexity.Linear
import codes.quine.labo.redos.automaton.Complexity.Polynomial
import codes.quine.labo.redos.util.Timeout
import upickle.default._

object Main {
  var checker: Checker = Checker.Hybrid

  def config: Config = Config(
    checker = checker,
    timeout = Timeout.from(10.second),
  )

  final case class RegExpInfo(
      `package`: String,
      version: String,
      path: String,
      line: Int,
      column: Int,
      source: String,
      flags: String
  )

  object RegExpInfo {
    implicit val rw: ReadWriter[RegExpInfo] = macroRW
  }

  final case class Result(
      info: RegExpInfo,
      time: Long,
      status: String,
      used: Option[String],
      attack: Option[String],
      complexity: Option[String],
      approximate: Option[Boolean],
      message: Option[String]
  )

  object Result {
    implicit val rw: ReadWriter[Result] = macroRW
  }

  def main(args: Array[String]): Unit = {
    val regexpJSON = args(0)
    println(s"==> Input: ${os.pwd / os.RelPath(regexpJSON)}")
    val outputJSON = args(1)
    println(s"==> Output: ${os.pwd / os.RelPath(outputJSON)}")
    checker = args(2) match {
      case "hybrid"    => Checker.Hybrid
      case "automaton" => Checker.Automaton
      case "fuzz"      => Checker.Fuzz
    }
    println(s"==> Checker: $checker")
    println()

    val infos = read[Seq[RegExpInfo]](os.read(os.pwd / os.RelPath(regexpJSON)))
    val counts = mutable.Map(
      "vulnerable" -> 0,
      "safe" -> 0,
      "timeout" -> 0,
      "error" -> 0
    )
    val results = infos.zipWithIndex.map { case (info, i) =>
      println(s"==> [${i + 1}/${infos.size}] (${counts.map { case (s, c) => s"$s: $c" }.mkString(", ")})")
      val result = test(info)
      counts(result.status) += 1
      result
    }
    os.write(os.pwd / os.RelPath(outputJSON), write(results))
  }

  def test(info: RegExpInfo): Result = {
    println(
      s"==> Test /${info.source}/${info.flags} (at ${info.`package`}@${info.version} ${info.path}:${info.line}:${info.column})"
    )
    val start = System.nanoTime()
    val diagnostics = ReDoS.check(info.source, info.flags, config)
    val time = System.nanoTime() - start
    println(s"==> Done in ${time / 1e9} s (used ${diagnostics.used})")

    System.gc()
    System.runFinalization()

    diagnostics match {
      case Vulnerable(attack, c, used) =>
        println(s"==> Result: vulnerable ($c)")
        val complexity = c match {
          case Some(Exponential(_))   => Some("exponential")
          case Some(Polynomial(d, _)) => Some(s"$d polynomial")
          case None                   => None
        }
        Result(info, time, "vulnerable", used.map(_.toString), Some(attack.asString), complexity, None, None)
      case Safe(c, used) =>
        println(s"==> Result: safe ($c)")
        val complexity = c match {
          case Some(Constant) => Some("constant")
          case Some(Linear)   => Some("linear")
          case None           => None
        }
        Result(info, time, "safe", used.map(_.toString), None, complexity, None, None)
      case Unknown(ErrorKind.Timeout, used) =>
        println(s"==> Result: timeout")
        Result(info, time, "timeout", used.map(_.toString), None, None, None, None)
      case Unknown(error, used) =>
        println(s"==> Result: error ($error)")
        Result(info, time, "error", used.map(_.toString), None, None, None, Some(error.toString))
    }
  }
}
