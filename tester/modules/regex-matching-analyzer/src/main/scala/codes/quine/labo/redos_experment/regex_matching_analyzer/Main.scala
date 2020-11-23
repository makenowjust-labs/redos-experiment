package codes.quine.labo.redos_experiment.regex_matching_analyzer_tester

import scala.collection.mutable

import matching.regexp.RegExp
import matching.regexp.RegExpParser
import matching.tool.Analysis
import matching.transition.Lookahead
import upickle.default._

object Main {
  val timeout: Option[Int] = Some(10)

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
    // regex-matching-analyzer does not support `g` and `m` flags, so it removes them from the flags.
    val flags = info.flags.replace("m", "").replace("g", "")
    println(
      s"==> Test /${info.source}/${flags} (at ${info.`package`}@${info.version} ${info.path}:${info.line}:${info.column})"
    )
    val start = System.nanoTime()
    val (result, _) = Analysis.runWithLimit(timeout) {
      val (pattern, opts) = RegExpParser.parsePCRE(s"/${info.source}/${flags}")
      val (degree, witness, approximate, _) = RegExp.calcTimeComplexity(pattern, opts, Some(Lookahead))
      degree match {
        case Some(0) => Result(info, 0, "safe", None, None, Some("constant"), Some(approximate), None)
        case Some(1) => Result(info, 0, "safe", None, None, Some("linear"), Some(approximate), None)
        case Some(d) =>
          Result(info, 0, "vulnerable", None, Some(witness.toString), Some(s"$d polynomial"), Some(approximate), None)
        case None =>
          Result(info, 0, "vulnerable", None, Some(witness.toString), Some("exponential"), Some(approximate), None)
      }
    }
    val time = System.nanoTime() - start
    println(s"==> Done in ${time / 1e9} s")

    System.gc()
    System.runFinalization()

    result match {
      case Analysis.Success(r) =>
        println(s"==> Result: ${r.status} (${r.attack})")
        r.copy(time = time)
      case Analysis.Failure(message) =>
        println(s"==> Result: error")
        Result(info, time, "error", None, None, None, None, Some(message))
      case Analysis.Timeout(message) =>
        println(s"==> Result: timeout")
        Result(info, time, "timeout", None, None, None, None, Some(message))
    }
  }
}
