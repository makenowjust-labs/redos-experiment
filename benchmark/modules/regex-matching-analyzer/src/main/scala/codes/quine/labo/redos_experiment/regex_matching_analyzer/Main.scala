package codes.quine.labo.redos_experiment.regex_matching_analyzer

import scala.collection.mutable

import codes.quine.labo.redos_experiment.common._
import matching.regexp.RegExp
import matching.regexp.RegExpParser
import matching.tool.Analysis
import matching.transition.Lookahead

object Main {
  val timeout: Option[Int] = Some(10)

  def main(args: Array[String]): Unit = {
    val inputPath = args(0)
    println(s"==> Input: $inputPath")
    val outputPath = args(1)
    println(s"==> Output: $outputPath")
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
      println(s"==> [$i/${infoSize}] (${counts.map { case (s, c) => s"$s: $c" }.mkString(", ")})")
      val result = test(info)
      counts(result.status) += 1
      result
    }
    println(s"==> [$infoSize/$infoSize] (${counts.map { case (s, c) => s"$s: $c" }.mkString(", ")})")
    IO.write(outputPath, results)
  }

  def test(info0: RegExpInfo): Result = {
    // regex-matching-analyzer does not support `g` and `m` flags, so it removes them from the flags.
    val flags = info0.flags.replace("m", "").replace("g", "")
    val info = info0.copy(flags = flags)
    println(s"==> Test $info")
    val start = System.nanoTime()
    val (result, _) = Analysis.runWithLimit(timeout) {
      val (pattern, opts) = RegExpParser.parsePCRE(s"/${info.source}/${flags}")
      val (degree, witness, approximateFlag, _) = RegExp.calcTimeComplexity(pattern, opts, Some(Lookahead))
      val approximate = if (approximateFlag) " (approximate)" else ""
      degree match {
        case Some(0) => Result(info, 0, Status.Safe, None, None, Some("constant" + approximate), None)
        case Some(1) => Result(info, 0, Status.Safe, None, None, Some("linear" + approximate), None)
        case Some(d) =>
          Result(info, 0, Status.Vulnerable, None, Some(witness.toString), Some(s"$d polynomial" + approximate), None)
        case None =>
          Result(info, 0, Status.Vulnerable, None, Some(witness.toString), Some("exponential" + approximate), None)
      }
    }
    val time = System.nanoTime() - start
    println(s"==> Done in ${time / 1e9} s")

    System.gc()
    System.runFinalization()

    result match {
      case Analysis.Success(r) =>
        println(s"==> Result: ${r.status}")
        r.copy(time = time)
      case Analysis.Failure(message) =>
        println(s"==> Result: error")
        Result(info, time, Status.Error, None, None, None, Some(message))
      case Analysis.Timeout(message) =>
        println(s"==> Result: timeout")
        Result(info, time, Status.Timeout, None, None, None, Some(message))
    }
  }
}
