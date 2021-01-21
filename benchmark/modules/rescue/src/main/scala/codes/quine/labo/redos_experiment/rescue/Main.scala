package codes.quine.labo.redos_experiment.rescue

import java.util.concurrent.atomic.AtomicReference

import scala.concurrent.ExecutionContext.Implicits._
import scala.concurrent._
import scala.util.control.NonFatal

import cn.edu.nju.moon.redos.attackers.GeneticAttacker
import cn.edu.nju.moon.redos.regex.ReScuePattern
import codes.quine.labo.redos_experiment.common._
import com.monovore.decline.Opts
import io.circe.Encoder

object Main extends Benchmarker {
  override def name: String = "rescue"
  override def version: String = "d197500"

  type Extra = Unit
  override def extraOpts: Opts[Unit] = Opts(())
  override def encodeExtra: Encoder[Unit] = Encoder.encodeUnit

  def test(info: RegExpInfo, bench: Benchmarker.Config[Extra]): Result = {
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
