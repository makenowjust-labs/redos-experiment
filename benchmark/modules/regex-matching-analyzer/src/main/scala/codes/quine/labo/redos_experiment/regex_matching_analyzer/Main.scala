package codes.quine.labo.redos_experiment.regex_matching_analyzer

import codes.quine.labo.redos_experiment.common._
import com.monovore.decline.Opts
import io.circe.Encoder
import matching.regexp.RegExp
import matching.regexp.RegExpParser
import matching.tool.Analysis
import matching.transition.Lookahead

object Main extends Benchmarker {
  def name: String = "regex-matching-analyzer"
  def version: String = "e1bb3ba"

  type Extra = Unit
  override def extraOpts: Opts[Unit] = Opts(())
  override def encodeExtra: Encoder[Unit] = Encoder.encodeUnit

  def test(info0: RegExpInfo, bench: Benchmarker.Config[Extra]): Result = {
    // regex-matching-analyzer does not support `g` and `m` flags, so it removes them from the flags.
    val flags = info0.flags.replace("m", "").replace("g", "")
    val info = info0.copy(flags = flags)
    val start = System.nanoTime()
    val (result, _) = Analysis.runWithLimit(Some(bench.timeout.toSeconds.toInt)) {
      val (pattern, opts) = RegExpParser.parsePCRE(s"/${info.source}/$flags")
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

    System.gc()
    System.runFinalization()

    result match {
      case Analysis.Success(r)       => r.copy(time = time)
      case Analysis.Failure(message) => Result(info, time, Status.Error, None, None, None, Some(message))
      case Analysis.Timeout(message) => Result(info, time, Status.Timeout, None, None, None, Some(message))
    }
  }
}
