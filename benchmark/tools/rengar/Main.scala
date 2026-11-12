package redos_experiment

import java.util.concurrent.Callable
import java.util.concurrent.TimeUnit
import java.util.concurrent.TimeoutException

import scala.jdk.CollectionConverters.*

import rengar.checker.StaticPipeline
import rengar.checker.StaticPipeline.Result.State
import rengar.config.GlobalConfig
import rengar.parser.RegexParser
import rengar.parser.charutil.CharUtil

object Main extends Wrapper {

  def check(source: String, flags: String): ujson.Obj = {
    // This is `StaticPipeline.runWithTimeOut` with the time limit of the tool.
    // It is not called, because it reports an exception of the analysis as a timeout too.
    val findAll = GlobalConfig.option.isMultipleVulnerabilityMode
    val run: Callable[StaticPipeline.Result] = () => StaticPipeline.run(source, RegexParser.Language.Java, findAll)
    val future = GlobalConfig.executor.submit(run)
    val result =
      try future.get(GlobalConfig.option.getTotalTimeout, TimeUnit.SECONDS)
      catch {
        case _: TimeoutException =>
          future.cancel(true)
          return ujson.Obj("status" -> "timeout")
      }
    result.state match {
      case State.Vulnerable =>
        val found = result.attacks.get(0)
        val pattern = found.getLeft.getType
        val complexity =
          if (pattern == "EOLS") exponential
          else if (pattern.startsWith("POLS_") || pattern.startsWith("PTLS_")) ujson.Obj("type" -> "polynomial")
          else throw new IllegalStateException(s"unknown pattern: $pattern")
        val attack = found.getRight
        val Seq(prefix, pump, suffix) =
          Seq("prefix", "attack", "postfix").map(name => CharUtil.toString(field(attack, name).asInstanceOf[Array[Int]]))
        val output = vulnerable(complexity, pumps(Seq(prefix), Seq(pump), suffix))
        output("pattern") = pattern
        output("repeat") = field(attack, "n").asInstanceOf[Int]
        output("disturb") = result.disturbType.getTypes.asScala.toSeq.map(_.name).sorted
        output
      case State.Normal      => safe
      case State.SyntaxError => error("SyntaxError")
      case State.InternalBug => error("InternalBug")
    }
  }

  // The attack string of the tool has no accessors of its parts.
  def field(target: AnyRef, name: String): AnyRef = {
    val field = target.getClass.getDeclaredField(name)
    field.setAccessible(true)
    field.get(target)
  }

  override def main(args: Array[String]): Unit = {
    super.main(args)
    // The threads of the pool keep the JVM alive, so the pool is shut down as the command of the tool does.
    GlobalConfig.executor.shutdownNow()
  }
}
