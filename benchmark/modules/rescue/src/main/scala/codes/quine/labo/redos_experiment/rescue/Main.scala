package codes.quine.labo.redos_experiment.rescue

import java.util.concurrent.atomic.AtomicReference

import scala.collection.mutable
import scala.concurrent._
import scala.concurrent.duration._
import scala.concurrent.ExecutionContext.Implicits._
import scala.util.control.NonFatal

import cn.edu.nju.moon.redos.attackers.GeneticAttacker
import cn.edu.nju.moon.redos.regex.ReScuePattern
import codes.quine.labo.redos_experiment.common._

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

  def test(info: RegExpInfo): Result = {
    println(s"==> Test $info")

    val start = System.nanoTime()
    val threadRef = new AtomicReference[Thread]()
    val future = Future(blocking {
      threadRef.set(Thread.currentThread())
      try {
        val pattern = ReScuePattern.compile(info.source)
        val attacker = new GeneticAttacker()
        Option(attacker.attack(pattern)) match {
          case Some(t) if t.attackSuccess() =>
            Result(info, 0, Status.Vulnerable, None, Some(t.str), None, None)
          case _ =>
            Result(info, 0, Status.Safe, None, None, None, None)
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
    println()

    System.gc()
    System.runFinalization()

    println(s"==> Result: ${result.status}")
    result.copy(time = time)
  }
}
