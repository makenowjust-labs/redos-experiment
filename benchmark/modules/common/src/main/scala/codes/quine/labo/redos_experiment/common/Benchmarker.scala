package codes.quine.labo.redos_experiment.common

import scala.concurrent.duration._
import cats.data.{Validated, ValidatedNel}
import cats.syntax.apply._
import com.monovore.decline.{Argument, Command, Opts}
import Benchmarker._

import scala.collection.mutable

abstract class Benchmarker {
  protected def name: String
  protected def version: String

  type Extra
  protected def extraOpts: Opts[Extra]

  val command: Command[CLIConfig[Extra]] =
    Command(name = s"redos-experiment-$name", header = s"A benchmarker for $name") {
      val inputPath = Opts.argument[String](metavar = "input")
      val outputPath = Opts.argument[String](metavar = "output")
      val timeout = Opts
        .option[Duration](
          long = "timeout",
          short = "t",
          help = "A timeout duration in a checking"
        )
        .withDefault(DefaultTimeout)

      (inputPath, outputPath, timeout, extraOpts).mapN(CLIConfig.apply[Extra])
    }

  def main(args: Array[String]): Unit = command.parse(args.toSeq, sys.env) match {
    case Left(help) =>
      System.err.println(help)
      System.exit(1)
    case Right(cli) => run(cli)
  }

  def run(cli: CLIConfig[Extra]): Unit = {
    // Shows a configuration.
    println("==> Configuration:")
    println(cli)
    println()

    // Reads a RegExp information file.
    val infoList = IO.read[Seq[RegExpInfo]](cli.inputPath)
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
      val result = test(info, cli)
      showResult(result)
      println()

      counts(result.status) += 1
      result
    }

    // Saves the results.
    showStat(infoSize)
    IO.write(cli.outputPath, results)
  }

  def test(info: RegExpInfo, cli: CLIConfig[Extra]): Result
}

object Benchmarker {
  val DefaultTimeout: Duration = 10.second

  case class CLIConfig[A](inputPath: String, outputPath: String, timeout: Duration, extra: A) {
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

  implicit val durationArgument: Argument[Duration] = new Argument[Duration] {
    override def read(string: String): ValidatedNel[String, Duration] =
      try Validated.validNel(Duration(string))
      catch {
        case ex: NumberFormatException => Validated.invalidNel(ex.getMessage)
      }

    override def defaultMetavar: String = "duration"
  }
}
