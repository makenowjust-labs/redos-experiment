package codes.quine.labo.redos_experiment.regex_static_analysis

import java.util.concurrent.atomic.AtomicReference

import scala.collection.mutable
import scala.concurrent._
import scala.concurrent.duration._
import scala.concurrent.ExecutionContext.Implicits._
import scala.util.control.NonFatal

import analysis.NFAAnalyserFlattening
import analysis.AnalysisSettings.PriorityRemovalStrategy
import analysis.AnalysisSettings.NFAConstruction
import analysis.NFAAnalyserInterface.{AnalysisResultsType, IdaAnalysisResultsIda}
import codes.quine.labo.redos_experiment.common._
import regexcompiler.MyPattern

object Main {
  val timeout: Duration = 10.second

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

  val analyser = new NFAAnalyserFlattening(PriorityRemovalStrategy.UNPRIORITISE)

  def test(info: RegExpInfo): Result = {
    println(s"==> Test $info")

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
      try Await.result(future, timeout)
      catch {
        case ex: TimeoutException => Result(info, 0, Status.Timeout, None, None, None, None)
      }
    val time = System.nanoTime() - start
    Option(threadRef.get).foreach(_.interrupt())

    println(s"==> Done in ${time / 1e9} s")

    System.gc()
    System.runFinalization()

    println(s"==> Result: ${result.status}")
    result.copy(time = time)
  }
}
