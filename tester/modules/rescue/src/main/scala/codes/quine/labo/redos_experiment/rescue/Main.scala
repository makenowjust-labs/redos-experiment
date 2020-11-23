package codes.quine.labo.redos_experiment.rescue

import java.util.concurrent.atomic.AtomicReference

import cn.edu.nju.moon.redos.attackers.GeneticAttacker
import cn.edu.nju.moon.redos.{RedosAttacker, Trace}

import scala.collection.mutable
import scala.concurrent._
import scala.concurrent.duration._
import scala.concurrent.ExecutionContext.Implicits._
import scala.util.control.NonFatal

import cn.edu.nju.moon.redos.regex.ReScuePattern
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

  def test(info: RegExpInfo): Result = {
    println(
      s"==> Test /${info.source}/${info.flags} (at ${info.`package`}@${info.version} ${info.path}:${info.line}:${info.column})"
    )

    val start = System.nanoTime()
    val threadRef = new AtomicReference[Thread]()
    val future = Future(blocking {
      threadRef.set(Thread.currentThread())
      try {
        val pattern = ReScuePattern.compile(info.source)
        val attacker = new GeneticAttacker()
        Option(attacker.attack(pattern)) match {
          case Some(t) if t.attackSuccess() =>
            Result(info, 0, "vulnerable", None, Some(t.str), None, None, None)
          case _ =>
            Result(info, 0, "safe", None, None, None, None, None)
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

    println(s"==> Done in ${time / 1e9} s")
    println()

    System.gc()
    System.runFinalization()

    result.copy(time = time)
  }
}
