package codes.quine.labo.redos_experiment.common

import java.time.ZoneOffset
import java.time.ZonedDateTime

import scala.collection.mutable
import scala.concurrent.duration._

import cats.data.Validated
import cats.data.ValidatedNel
import cats.syntax.apply._
import com.monovore.decline.Argument
import com.monovore.decline.Command
import com.monovore.decline.Opts
import io.circe.Encoder
import io.circe.generic.semiauto.deriveEncoder

abstract class Benchmarker {
  def name: String
  def version: String

  type Extra
  def extraOpts: Opts[Extra]
  implicit def encodeExtra: Encoder[Extra]

  val command: Command[Benchmarker.Config[Extra]] =
    Command(name = s"redos-experiment-$name", header = s"A benchmarker for $name") {
      val inputPath = Opts.argument[String](metavar = "input")
      val outputPath = Opts.argument[String](metavar = "output")
      val timeout = Opts
        .option[Duration](
          long = "timeout",
          short = "t",
          help = "A timeout duration in a checking"
        )(Benchmarker.durationArgument)
        .withDefault(Benchmarker.DefaultTimeout)

      (inputPath, outputPath, timeout, extraOpts).mapN(Benchmarker.Config[Extra])
    }

  def main(args: Array[String]): Unit = command.parse(args.toSeq, sys.env) match {
    case Left(help) =>
      System.err.println(help)
      System.exit(1)
    case Right(bench) => run(bench)
  }

  def run(bench: Benchmarker.Config[Extra]): Unit = {
    // Shows a configuration.
    println("==> Configuration:")
    println(bench)
    println()

    val startTime = ZonedDateTime.now(ZoneOffset.UTC)

    // Reads a RegExp information file.
    val infoList = IO.read[Seq[RegExpInfo]](bench.inputPath)
    val infoSize = infoList.size

    val counts = mutable.Map(
      Status.Safe -> 0,
      Status.Vulnerable -> 0,
      Status.Timeout -> 0,
      Status.Error -> 0
    )

    def showStat(i: Int): Unit =
      println(s"==> [$i/$infoSize] (${counts.map { case (s, c) => s"$s: $c" }.mkString(", ")})")
    def showInfo(info: RegExpInfo): Unit =
      println(s"==> Test $info")
    def showResult(result: Result): Unit = {
      println("==> Result:")
      println(result)
    }

    // Runs tests against RegExp.
    val results = infoList.zipWithIndex.map { case (info, i) =>
      showStat(i)
      showInfo(info)
      val result = test(info, bench)
      showResult(result)
      println()

      counts(result.status) += 1
      result
    }
    showStat(infoSize)

    // Saves the results.
    val endTime = ZonedDateTime.now(ZoneOffset.UTC)
    val resultSet = ResultSet(name, version, startTime, endTime, bench, results)
    IO.write(bench.outputPath, resultSet)
  }

  def test(info: RegExpInfo, bench: Benchmarker.Config[Extra]): Result
}

object Benchmarker {
  val DefaultTimeout: Duration = 10.second

  final case class Config[A](inputPath: String, outputPath: String, timeout: Duration, extra: A) {
    override def toString: String = {
      val extraString = extra.toString
      val extraPart =
        if (extraString == "()") "" else s"\n$extraString"
      s"""|Input  : $inputPath
          |Output : $outputPath
          |Timeout: $timeout
          |""".stripMargin + extraPart
    }
  }

  object Config {
    implicit def encodeConfig[A: Encoder]: Encoder[Config[A]] = deriveEncoder
  }

  implicit val durationArgument: Argument[Duration] = new Argument[Duration] {
    override def read(string: String): ValidatedNel[String, Duration] =
      try Validated.validNel(Duration(string))
      catch {
        case ex: NumberFormatException => Validated.invalidNel(ex.getMessage)
      }

    override def defaultMetavar: String = "duration"
  }

  implicit def encodeDuration: Encoder[Duration] = Encoder.encodeString.contramap(_.toString)
}
