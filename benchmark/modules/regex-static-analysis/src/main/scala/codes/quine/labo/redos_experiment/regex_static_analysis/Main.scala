package codes.quine.labo.redos_experiment.regex_static_analysis

import java.util.concurrent.atomic.AtomicReference

import scala.concurrent.ExecutionContext.Implicits._
import scala.concurrent._
import scala.util.control.NonFatal

import analysis.AnalysisSettings.NFAConstruction
import analysis.AnalysisSettings.PriorityRemovalStrategy
import analysis.NFAAnalyserFlattening
import analysis.NFAAnalyserInterface.AnalysisResultsType
import analysis.NFAAnalyserInterface.IdaAnalysisResultsIda
import codes.quine.labo.redos_experiment.common._
import com.monovore.decline.Opts
import io.circe.Encoder
import regexcompiler.MyPattern

object Main extends Benchmarker {
  override def name: String = "regex-static-analysis"
  override def version: String = "cd1ea68"

  type Extra = Unit
  override def extraOpts: Opts[Unit] = Opts(())
  override def encodeExtra: Encoder[Unit] = Encoder.encodeUnit

  val analyser = new NFAAnalyserFlattening(PriorityRemovalStrategy.UNPRIORITISE)

  def test(info: RegExpInfo, bench: Benchmarker.Config[Extra]): Result = {
    val start = System.nanoTime()
    val threadRef = new AtomicReference[Thread]()
    val future = Future(blocking {
      threadRef.set(Thread.currentThread())
      try {
        val graph = MyPattern.toNFAGraph(info.source, NFAConstruction.JAVA)
        analyser.containsEDA(graph) match {
          case AnalysisResultsType.EDA =>
            val attack = analyser.findEDAExploitString(graph).toString
            Result(info, 0, Status.Vulnerable, None, Some(attack), Some("exponential"), None)
          case AnalysisResultsType.ANALYSIS_FAILED =>
            Result(info, 0, Status.Error, None, None, None, Some("failed on EDA analysis"))
          case AnalysisResultsType.NO_EDA =>
            analyser.containsIDA(graph) match {
              case AnalysisResultsType.IDA =>
                val results = analyser.getIdaAnalysisResults(graph).asInstanceOf[IdaAnalysisResultsIda]
                val attack = analyser.findIDAExploitString(graph).toString
                Result(info, 0, Status.Vulnerable, None, Some(attack), Some(s"${results.getDegree} polynomial"), None)
              case AnalysisResultsType.NO_IDA =>
                Result(info, 0, Status.Safe, None, None, None, None)
              case AnalysisResultsType.ANALYSIS_FAILED =>
                Result(info, 0, Status.Error, None, None, None, Some("failed on IDA analysis"))
              case r => sys.error(s"unexpected result on IDA analysis: $r")
            }
          case r => sys.error(s"unexpected result on EDA analysis: $r")
        }
      } catch {
        case NonFatal(ex) =>
          Result(info, 0, Status.Error, None, None, None, Some(ex.getMessage))
      }
    })

    val result =
      try Await.result(future, bench.timeout)
      catch {
        case _: TimeoutException => Result(info, 0, Status.Timeout, None, None, None, None)
      }
    val time = System.nanoTime() - start
    Option(threadRef.get).foreach(_.interrupt())

    System.gc()
    System.runFinalization()

    result.copy(time = time)
  }
}
