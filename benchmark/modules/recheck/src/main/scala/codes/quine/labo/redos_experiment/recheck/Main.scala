package codes.quine.labo.redos_experiment.recheck

import scala.util.Random

import cats.data.Validated
import cats.syntax.apply._
import codes.quine.labo.recheck.Config
import codes.quine.labo.recheck.ReDoS
import codes.quine.labo.recheck.common.Checker
import codes.quine.labo.recheck.common.Context
import codes.quine.labo.recheck.diagnostics.Diagnostics
import codes.quine.labo.redos_experiment.common._
import com.monovore.decline.Opts
import io.circe.Encoder
import io.circe.generic.semiauto.deriveEncoder

object Main extends Benchmarker {
  override def name: String = "recheck"
  override def version: String = "3.0.0"

  final case class Extra(
      checker: Checker,
      maxAttackSize: Int,
      attackLimit: Int,
      randomSeed: Long,
      seedLimit: Int,
      incubationLimit: Int,
      crossSize: Int,
      mutateSize: Int,
      maxSeedSize: Int,
      maxGenerationSize: Int,
      maxIteration: Int,
      maxDegree: Int,
      heatRate: Double,
      usesAcceleration: Boolean,
      maxRepeatCount: Int,
      maxNFASize: Int,
      maxPatternSize: Int
  ) {
    override def toString: String =
      s"""|Checker            : ${checker.toString.toLowerCase}
          |Max attack size    : $maxAttackSize
          |Attack limit       : $attackLimit
          |Random seed        : $randomSeed
          |Seed limit         : $seedLimit
          |Incubation limit   : $incubationLimit
          |Cross size         : $crossSize
          |Mutate size        : $mutateSize
          |Max seed size      : $maxSeedSize
          |Max generation size: $maxGenerationSize
          |Max iteration      : $maxIteration
          |Max degree         : $maxDegree
          |Heat rate          : $heatRate
          |Uses acceleration  : $usesAcceleration
          |Max repeat count   : $maxRepeatCount
          |Max NFA size       : $maxNFASize
          |Max pattern size   : $maxPatternSize
          |""".stripMargin
  }

  override def extraOpts: Opts[Extra] = {
    val checker = Opts
      .option[String](
        long = "checker",
        short = "c",
        help = "A checker name to be used (one of 'hybrid', 'automaton', or 'fuzz')",
        metavar = "name"
      )
      .mapValidated {
        case "hybrid"    => Validated.validNel(Checker.Hybrid)
        case "automaton" => Validated.validNel(Checker.Automaton)
        case "fuzz"      => Validated.validNel(Checker.Fuzz)
        case s           => Validated.invalidNel(s"Invalid value '$s'")
      }
      .withDefault(Checker.Hybrid)
    val maxAttackSize = Opts
      .option[Int](long = "max-attack-size", help = "A maximum length of an attack string")
      .withDefault(Config.MaxAttackSize)
    val attackLimit = Opts
      .option[Int](long = "attack-limit", help = "A limit of VM execution steps.")
      .withDefault(Config.AttackLimit)
    val randomSeed = Opts
      .option[Long](long = "random-seed", help = "A PRNG seed")
      .withDefault(42L)
    val seedLimit = Opts
      .option[Int](long = "seed-limit", help = "A limit of VM execution steps on the seeding phase")
      .withDefault(Config.SeedLimit)
    val incubationLimit = Opts
      .option[Int](long = "incubation-limit", help = "A limit of VM execution steps on the incubation phase")
      .withDefault(Config.IncubationLimit)
    val crossSize = Opts
      .option[Int](long = "cross-size", help = "The number of crossings on one generation")
      .withDefault(Config.CrossSize)
    val mutateSize = Opts
      .option[Int](long = "mutate-size", help = "The number of mutations on one generation")
      .withDefault(Config.MutateSize)
    val maxSeedSize = Opts
      .option[Int](long = "max-seed-size", help = "A maximum size of a seed set")
      .withDefault(Config.MaxSeedSize)
    val maxGenerationSize = Opts
      .option[Int](long = "max-generation-size", help = "A maximum size of a living population on one generation")
      .withDefault(Config.MaxGenerationSize)
    val maxIteration = Opts
      .option[Int](long = "max-iteration", help = "The number of iterations on the incubation phase")
      .withDefault(Config.MaxIteration)
    val maxDegree = Opts
      .option[Int](long = "max-degree", help = "A maximum degree to attempt on building an attack string")
      .withDefault(Config.MaxDegree)
    val heatRate = Opts
      .option[Double](long = "heat-rate", help = "A rate of a hotspot steps by the maximum steps.")
      .withDefault(Config.HeatRate)
    val noAcceleration = Opts
      .flag(long = "no-acceleration", help = "Don't use acceleration")
      .orTrue
    val maxRepeatCount = Opts
      .option[Int](long = "max-repeat-count", help = "A limit of repetition count in the RegExp")
      .withDefault(Config.MaxRepeatCount)
    val maxNFASize = Opts
      .option[Int](long = "max-nfa-size", help = "A maximum size of the transition function of NFA")
      .withDefault(Config.MaxNFASize)
    val maxPatternSize = Opts
      .option[Int](long = "max-pattern-size", help = "A maximum size of the pattern")
      .withDefault(Config.MaxPatternSize)
    (
      checker,
      maxAttackSize,
      attackLimit,
      randomSeed,
      seedLimit,
      incubationLimit,
      crossSize,
      mutateSize,
      maxSeedSize,
      maxGenerationSize,
      maxIteration,
      maxDegree,
      heatRate,
      noAcceleration,
      maxRepeatCount,
      maxNFASize,
      maxPatternSize
    ).mapN(Extra)
  }
  override def encodeExtra: Encoder[Extra] = deriveEncoder
  implicit def encodeChecker: Encoder[Checker] = Encoder.encodeString.contramap {
    case Checker.Hybrid => "hybrid"
    case Checker.Automaton => "automaton"
    case Checker.Fuzz => "fuzz"
  }

  def test(info: RegExpInfo, bench: Benchmarker.Config[Extra]): Result = {
    val config = Config(
      context = Context(timeout = bench.timeout),
      checker = bench.extra.checker,
      maxAttackSize = bench.extra.maxAttackSize,
      attackLimit = bench.extra.attackLimit,
      random = new Random(bench.extra.randomSeed),
      seedLimit = bench.extra.seedLimit,
      incubationLimit = bench.extra.incubationLimit,
      crossSize = bench.extra.crossSize,
      mutateSize = bench.extra.mutateSize,
      maxSeedSize = bench.extra.maxSeedSize,
      maxGenerationSize = bench.extra.maxGenerationSize,
      maxIteration = bench.extra.maxIteration,
      maxDegree = bench.extra.maxDegree,
      heatRate = bench.extra.heatRate,
      usesAcceleration = bench.extra.usesAcceleration,
      maxRepeatCount = bench.extra.maxRepeatCount,
      maxNFASize = bench.extra.maxNFASize,
      maxPatternSize = bench.extra.maxPatternSize
    )
    val start = System.nanoTime()
    val diagnostics = ReDoS.check(info.source, info.flags, config)
    val time = System.nanoTime() - start

    System.gc()
    System.runFinalization()

    diagnostics match {
      case Diagnostics.Vulnerable(_, _, complexity, attack, _, checker) =>
        Result(info, time, Status.Vulnerable, Some(checker.toString), Some(attack.toString), Some(complexity.toString), None)
      case Diagnostics.Safe(_, _, complexity, checker) =>
        Result(info, time, Status.Safe, Some(checker.toString), None, Some(complexity.toString), None)
      case Diagnostics.Unknown(_, _, Diagnostics.ErrorKind.Timeout, checker) =>
        Result(info, time, Status.Timeout, checker.map(_.toString), None, None, None)
      case Diagnostics.Unknown(_, _, error, checker) =>
        Result(info, time, Status.Error, checker.map(_.toString), None, None, Some(error.toString))
    }
  }
}
