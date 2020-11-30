package codes.quine.labo.redos_experiment.regex_static_analysis

import java.util.concurrent.atomic.AtomicReference

import scala.collection.mutable
import scala.concurrent._
import scala.concurrent.duration._
import scala.concurrent.ExecutionContext.Implicits._
import scala.util.control.NonFatal
import analysis.{IdaAnalysisResults, NFAAnalyserFlattening}
import analysis.AnalysisSettings.PriorityRemovalStrategy
import analysis.AnalysisSettings.NFAConstruction
import analysis.NFAAnalyserInterface.{AnalysisResultsType, IdaAnalysisResultsIda}
import regexcompiler.MyPattern
import upickle.default._

object Main {
  val timeout: Duration = 10.second

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

  val analyser = new NFAAnalyserFlattening(PriorityRemovalStrategy.UNPRIORITISE)

  def test(info: RegExpInfo): Result = {
    println(
      s"==> Test /${info.source}/${info.flags} (at ${info.`package`}@${info.version} ${info.path}:${info.line}:${info.column})"
    )

    val start = System.nanoTime()
    val threadRef = new AtomicReference[Thread]()
    val future = Future(blocking {
      threadRef.set(Thread.currentThread())
      try {
        val graph = MyPattern.toNFAGraph(info.source, NFAConstruction.JAVA)
        analyser.containsEDA(graph) match {
          case AnalysisResultsType.EDA =>
            val attack = analyser.findEDAExploitString(graph).toString
            Result(info, 0, "vulnerable", None, Some(attack), Some("exponential"), None, None)
          case AnalysisResultsType.ANALYSIS_FAILED =>
            Result(info, 0, "error", None, None, None, None, Some("failed on EDA analysis"))
          case AnalysisResultsType.NO_EDA =>
            analyser.containsIDA(graph) match {
              case AnalysisResultsType.IDA =>
                val results = analyser.getIdaAnalysisResults(graph).asInstanceOf[IdaAnalysisResultsIda]
                val attack = analyser.findIDAExploitString(graph).toString
                Result(info, 0, "vulnerable", None, Some(attack), Some(s"${results.getDegree} polynomial"), None, None)
              case AnalysisResultsType.NO_IDA =>
                Result(info, 0, "safe", None, None, None, None, None)
              case AnalysisResultsType.ANALYSIS_FAILED =>
                Result(info, 0, "error", None, None, None, None, Some("failed on IDA analysis"))
              case r => sys.error(s"unexpected result on IDA analysis: $r")
            }
          case r => sys.error(s"unexpected result on EDA analysis: $r")
        }
      } catch {
        case NonFatal(ex) =>
          Result(info, 0, "error", None, None, None, None, Some(ex.getMessage))
      }
    })

    val result = try Await.result(future, timeout) catch {
      case ex: TimeoutException => Result(info, 0, "timeout", None, None, None, None, None)
    }
    val time = System.nanoTime() - start
    Option(threadRef.get).foreach(_.interrupt())

    println(s"==> Result: ${result.status} (${result.complexity}, ${result.attack})")
    println(s"==> Done in ${time / 1e9} s")

    System.gc()
    System.runFinalization()

    result.copy(time = time)
  }
}