package codes.quine.labo.redos_experiment.recheck

import cats.data.{Validated, ValidatedNel}
import cats.syntax.apply._
import codes.quine.labo.recheck.ReDoS
import codes.quine.labo.recheck.common.{AccelerationMode, Checker, Context, Parameters, Seeder}
import codes.quine.labo.recheck.diagnostics.Diagnostics
import codes.quine.labo.recheck.codec.encodeChecker
import codes.quine.labo.redos_experiment.common._
import com.monovore.decline.{Argument, Opts}
import io.circe.Encoder
import io.circe.generic.semiauto.deriveEncoder

import java.time.LocalDateTime
import scala.concurrent.duration.Duration

object Main extends Benchmarker {
  override def name: String = "recheck"
  override def version: String = "4.1.1+106-3972dfc8-SNAPSHOT"

  /** Log is a logging mode. */
  sealed abstract class Log extends Product with Serializable

  object Log {
    case object On extends Log {
      override def toString: String = "on"
    }
    case object Off extends Log {
      override def toString: String = "off"
    }
    case object Save extends Log {
      override def toString: String = "save"
    }

    implicit val encode: Encoder[Log] = deriveEncoder

    implicit val argument: Argument[Log] = new Argument[Log] {
      def read(string: String): ValidatedNel[String, Log] = string match {
        case "on"   => Validated.validNel(Log.On)
        case "off"  => Validated.validNel(Log.Off)
        case "save" => Validated.validNel(Log.Save)
        case s      => Validated.invalidNel(s"unknown log: $s")
      }

      def defaultMetavar: String = "log"
    }
  }

  final case class Extra(
      checker: Checker,
      log: Log,
      maxAttackStringSize: Int,
      attackLimit: Int,
      randomSeed: Long,
      maxIteration: Int,
      seeder: Seeder,
      maxSimpleRepeatCount: Int,
      seedingLimit: Int,
      seedingTimeout: Duration,
      maxInitialGenerationSize: Int,
      incubationLimit: Int,
      incubationTimeout: Duration,
      maxGeneStringSize: Int,
      maxGenerationSize: Int,
      crossoverSize: Int,
      mutationSize: Int,
      attackTimeout: Duration,
      maxDegree: Int,
      heatRatio: Double,
      accelerationMode: AccelerationMode,
      maxRepeatCount: Int,
      maxNFASize: Int,
      maxPatternSize: Int
  ) {
    override def toString: String =
      s"""|Checker                     : ${checker.toString.toLowerCase}
          |Log                         : ${log}
          |Max attack string size      : $maxAttackStringSize
          |Attack limit                : $attackLimit
          |Random seed                 : $randomSeed
          |Max iteration               : $maxIteration
          |Seeder                      : $seeder
          |Max simple repeat count     : $maxSimpleRepeatCount
          |Seeding limit               : $seedingLimit
          |Seeding timeout             : $seedingTimeout
          |Max initial generation size : $maxInitialGenerationSize
          |Incubation limit            : $incubationLimit
          |Incubation timeout          : $incubationTimeout
          |Max gene string size        : $maxGeneStringSize
          |Max generation size         : $maxGenerationSize
          |Crossover size              : $crossoverSize
          |Mutation size               : $mutationSize
          |Attack timeout              : $attackTimeout
          |Max degree                  : $maxDegree
          |Heat ratio                  : $heatRatio
          |Acceleration mode           : $accelerationMode
          |Max repeat count            : $maxRepeatCount
          |Max NFA size                : $maxNFASize
          |Max pattern size            : $maxPatternSize
          |""".stripMargin
  }

  override def extraOpts: Opts[Extra] = {
    implicit val durationArgument: Argument[Duration] = new Argument[Duration] {
      def read(string: String): ValidatedNel[String, Duration] =
        try Validated.validNel(Duration(string))
        catch {
          case _: NumberFormatException => Validated.invalidNel(s"invalid duration: $string")
        }

      def defaultMetavar: String = "duration"
    }

    implicit val checkerArgument: Argument[Checker] = new Argument[Checker] {
      def read(string: String): ValidatedNel[String, Checker] = string match {
        case "hybrid"    => Validated.validNel(Checker.Hybrid)
        case "automaton" => Validated.validNel(Checker.Automaton)
        case "fuzz"      => Validated.validNel(Checker.Fuzz)
        case s           => Validated.invalidNel(s"unknown checker: $s")
      }

      def defaultMetavar: String = "checker"
    }

    implicit val accelerationModeArgument: Argument[AccelerationMode] = new Argument[AccelerationMode] {
      def read(string: String): ValidatedNel[String, AccelerationMode] = string match {
        case "auto" => Validated.validNel(AccelerationMode.Auto)
        case "on"   => Validated.validNel(AccelerationMode.On)
        case "off"  => Validated.validNel(AccelerationMode.Off)
        case s      => Validated.invalidNel(s"unknown acceleration mode: $s")
      }

      def defaultMetavar: String = "mode"
    }

    implicit val seederArgument: Argument[Seeder] = new Argument[Seeder] {
      def read(string: String): ValidatedNel[String, Seeder] = string match {
        case "static"  => Validated.validNel(Seeder.Static)
        case "dynamic" => Validated.validNel(Seeder.Dynamic)
        case s         => Validated.invalidNel(s"unknown seeder: $s")
      }

      def defaultMetavar: String = "seeder"
    }

    val checker = Opts
      .option[Checker](long = "checker", short = "c", help = "Type of checker used for analysis.")
      .withDefault(Parameters.Checker)
    val log = Opts
      .option[Log](long = "log", help = "Log mode")
      .withDefault(Log.Off)
    val maxAttackStringSize = Opts
      .option[Int](long = "max-attack-string-size", help = "Maximum length of an attack string.")
      .withDefault(Parameters.MaxAttackStringSize)
    val attackLimit = Opts
      .option[Int](
        long = "attack-limit",
        help = "Upper limit on the number of characters read by the VM during attack string construction."
      )
      .withDefault(Parameters.AttackLimit)
    val randomSeed = Opts
      .option[Long](long = "random-seed", help = "Seed value for PRNG used by fuzzing.")
      .withDefault(Parameters.RandomSeed)
    val maxIteration = Opts
      .option[Int](long = "max-iteration", help = "Maximum number of iterations of genetic algorithm.")
      .withDefault(Parameters.MaxIteration)
    val seeder = Opts
      .option[Seeder](
        long = "seeder",
        help = "Type of seeder used for constructing the initial generation of fuzzing."
      )
      .withDefault(Parameters.Seeder)
    val maxSimpleRepeatCount = Opts
      .option[Int](
        long = "max-simple-repeat-count",
        help = "Maximum number of sum of repeat counts for static seeder."
      )
      .withDefault(Parameters.MaxSimpleRepeatCount)
    val seedingLimit = Opts
      .option[Int](
        long = "seeding-limit",
        help = "Upper limit on the number of characters read by the VM during seeding."
      )
      .withDefault(Parameters.SeedingLimit)
    val seedingTimeout = Opts
      .option[Duration](long = "seeding-timeout", help = "Upper limit of VM execution time during seeding.")
      .withDefault(Parameters.SeedingTimeout)
    val maxInitialGenerationSize = Opts
      .option[Int](long = "max-initial-generation-size", help = "Maximum population at the initial generation.")
      .withDefault(Parameters.MaxInitialGenerationSize)
    val incubationLimit = Opts
      .option[Int](
        long = "incubation-limit",
        help = "Upper limit on the number of characters read by the VM during incubation."
      )
      .withDefault(Parameters.IncubationLimit)
    val incubationTimeout = Opts
      .option[Duration](long = "incubation-timeout", help = "Upper limit of VM execution time during incubation.")
      .withDefault(Parameters.IncubationTimeout)
    val maxGeneStringSize = Opts
      .option[Int](
        long = "max-gene-string-size",
        help = "Maximum length of an attack string on genetic algorithm iterations."
      )
      .withDefault(Parameters.MaxGeneStringSize)
    val maxGenerationSize = Opts
      .option[Int](long = "max-generation-size", help = "Maximum population at a single generation.")
      .withDefault(Parameters.MaxGenerationSize)
    val crossoverSize = Opts
      .option[Int](long = "crossover-size", help = "Number of crossovers in a single generation.")
      .withDefault(Parameters.CrossoverSize)
    val mutationSize = Opts
      .option[Int](long = "mutation-size", help = "Number of mutations in a single generation.")
      .withDefault(Parameters.MutationSize)
    val attackTimeout = Opts
      .option[Duration](
        long = "attack-timeout",
        help = "The upper limit of the VM execution time when constructing a attack string."
      )
      .withDefault(Parameters.AttackTimeout)
    val maxDegree = Opts
      .option[Int](long = "max-degree", help = "Maximum degree for constructing attack string.")
      .withDefault(Parameters.MaxDegree)
    val heatRatio = Opts
      .option[Double](
        long = "heat-ratio",
        help = "Ratio of the number of characters read to the maximum number to be considered a hotspot."
      )
      .withDefault(Parameters.HeatRatio)
    val accelerationMode = Opts
      .option[AccelerationMode](long = "acceleration-mode", help = "Acceleration mode for VM execution.")
      .withDefault(Parameters.AccelerationMode)
    val maxRepeatCount = Opts
      .option[Int](long = "max-repeat-count", help = "Maximum number of sum of repeat counts.")
      .withDefault(Parameters.MaxRepeatCount)
    val maxNFASize = Opts
      .option[Int](long = "max-nfa-size", help = "Maximum transition size of NFA to use the automaton checker.")
      .withDefault(Parameters.MaxNFASize)
    val maxPatternSize = Opts
      .option[Int](long = "max-pattern-size", help = "Maximum pattern size to use the automaton checker.")
      .withDefault(Parameters.MaxPatternSize)

    (
      (
        checker,
        log,
        maxAttackStringSize,
        attackLimit,
        randomSeed,
        maxIteration,
        seeder,
        maxSimpleRepeatCount,
        seedingLimit,
        seedingTimeout,
        maxInitialGenerationSize
      ).tupled,
      (
        incubationLimit,
        incubationTimeout,
        maxGeneStringSize,
        maxGenerationSize,
        crossoverSize,
        mutationSize,
        attackTimeout,
        maxDegree,
        heatRatio,
        accelerationMode,
        maxRepeatCount,
        maxNFASize,
        maxPatternSize
      ).tupled
    ).mapN {
      case (
            (
              checker,
              log,
              maxAttackStringSize,
              attackLimit,
              randomSeed,
              maxIteration,
              seeder,
              maxSimpleRepeatCount,
              seedingLimit,
              seedingTimeout,
              maxInitialGenerationSize
            ),
            (
              incubationLimit,
              incubationTimeout,
              maxGeneStringSize,
              maxGenerationSize,
              crossoverSize,
              mutationSize,
              attackTimeout,
              maxDegree,
              heatRatio,
              accelerationMode,
              maxRepeatCount,
              maxNFASize,
              maxPatternSize
            )
          ) =>
        Extra(
          checker,
          log,
          maxAttackStringSize,
          attackLimit,
          randomSeed,
          maxIteration,
          seeder,
          maxSimpleRepeatCount,
          seedingLimit,
          seedingTimeout,
          maxInitialGenerationSize,
          incubationLimit,
          incubationTimeout,
          maxGeneStringSize,
          maxGenerationSize,
          crossoverSize,
          mutationSize,
          attackTimeout,
          maxDegree,
          heatRatio,
          accelerationMode,
          maxRepeatCount,
          maxNFASize,
          maxPatternSize
        )
    }
  }
  override def encodeExtra: Encoder[Extra] = deriveEncoder
  implicit def encodeDuration: Encoder[Duration] = Encoder.encodeString.contramap(_.toString)
  implicit def encodeAccelerationMode: Encoder[AccelerationMode] = Encoder.encodeString.contramap(_.toString)
  implicit def encodeSeeder: Encoder[Seeder] = Encoder.encodeString.contramap(_.toString)

  def test(info: RegExpInfo, bench: Benchmarker.Config[Extra]): Result = {
    var log: StringBuilder = null
    val logger = bench.extra.log match {
      case Log.On =>
        Some[Context.Logger] { message =>
          val date = LocalDateTime.now()
          Console.out.println(s"[$date] $message")
        }
      case Log.Off => None
      case Log.Save =>
        log = new StringBuilder
        Some[Context.Logger] { message => log.append(message).append('\n') }
    }
    val params = Parameters(
      timeout = bench.timeout,
      checker = bench.extra.checker,
      logger = logger,
      maxAttackStringSize = bench.extra.maxAttackStringSize,
      attackLimit = bench.extra.attackLimit,
      randomSeed = bench.extra.randomSeed,
      maxIteration = bench.extra.maxIteration,
      seeder = bench.extra.seeder,
      maxSimpleRepeatCount = bench.extra.maxSimpleRepeatCount,
      seedingLimit = bench.extra.seedingLimit,
      seedingTimeout = bench.extra.seedingTimeout,
      maxInitialGenerationSize = bench.extra.maxInitialGenerationSize,
      incubationLimit = bench.extra.incubationLimit,
      incubationTimeout = bench.extra.incubationTimeout,
      maxGeneStringSize = bench.extra.maxGeneStringSize,
      maxGenerationSize = bench.extra.maxGenerationSize,
      crossoverSize = bench.extra.crossoverSize,
      mutationSize = bench.extra.mutationSize,
      maxDegree = bench.extra.maxDegree,
      heatRatio = bench.extra.heatRatio,
      accelerationMode = bench.extra.accelerationMode,
      maxRepeatCount = bench.extra.maxRepeatCount,
      maxNFASize = bench.extra.maxNFASize,
      maxPatternSize = bench.extra.maxPatternSize
    )
    val start = System.nanoTime()
    val diagnostics = ReDoS.check(info.source, info.flags, params)
    val time = System.nanoTime() - start

    System.gc()
    System.runFinalization()

    diagnostics match {
      case Diagnostics.Vulnerable(_, _, complexity, attack, _, checker) =>
        Result(
          info,
          time,
          Status.Vulnerable,
          Some(checker.toString),
          Some(attack.toString),
          Some(complexity.toString),
          Option(log).map(_.toString),
        )
      case Diagnostics.Safe(_, _, complexity, checker) =>
        Result(
          info,
          time,
          Status.Safe,
          Some(checker.toString),
          None,
          Some(complexity.toString),
          Option(log).map(_.result())
        )
      case Diagnostics.Unknown(_, _, Diagnostics.ErrorKind.Timeout, checker) =>
        Result(info, time, Status.Timeout, checker.map(_.toString), None, None, Option(log).map(_.result()))
      case Diagnostics.Unknown(_, _, error, checker) =>
        Result(
          info,
          time,
          Status.Error,
          checker.map(_.toString),
          None,
          None,
          Option(log) match {
            case Some(log) => Some(log.result() ++ "\n\nError:\n" ++ error.toString)
            case None      => Some(error.toString)
          }
        )
    }
  }
}
