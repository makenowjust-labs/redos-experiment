package codes.quine.labo.redos_experiment.redos

import scala.util.Random

import cats.data.Validated
import cats.syntax.apply._
import codes.quine.labo.redos.Checker
import codes.quine.labo.redos.Config
import codes.quine.labo.redos.Diagnostics._
import codes.quine.labo.redos.ReDoS
import codes.quine.labo.redos.automaton.Complexity._
import codes.quine.labo.redos.util.Timeout
import codes.quine.labo.redos_experiment.common._
import codes.quine.labo.redos_experiment.common.Benchmarker.CLIConfig
import com.monovore.decline.Opts

object Main extends Benchmarker {
  override protected def name: String = "redos"
  override protected def version: String = "1.2.0"

  final case class Extra(
      checker: Checker,
      maxAttackSize: Int,
      attackLimit: Int,
      randomSeed: Long,
      seedLimit: Int,
      populationLimit: Int,
      crossSize: Int,
      mutateSize: Int,
      maxSeedSize: Int,
      maxGenerationSize: Int,
      maxIteration: Int,
      maxDegree: Int,
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
          |Population limit   : $populationLimit
          |Cross size         : $crossSize
          |Mutate size        : $mutateSize
          |Max seed size      : $maxSeedSize
          |Max generation size: $maxGenerationSize
          |Max iteration      : $maxIteration
          |Max degree         : $maxDegree
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
    val populationLimit = Opts
      .option[Int](long = "population-limit", help = "A limit of VM execution steps on the incubation phase")
      .withDefault(Config.PopulationLimit)
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
      populationLimit,
      crossSize,
      mutateSize,
      maxSeedSize,
      maxGenerationSize,
      maxIteration,
      maxDegree,
      maxRepeatCount,
      maxNFASize,
      maxPatternSize
    ).mapN(Extra)
  }

  def test(info: RegExpInfo, cli: CLIConfig[Extra]): Result = {
    val config = Config(
      checker = cli.extra.checker,
      timeout = Timeout.from(cli.timeout),
      random = new Random(cli.extra.randomSeed)
    )
    val start = System.nanoTime()
    val diagnostics = ReDoS.check(info.source, info.flags, config)
    val time = System.nanoTime() - start
    val used = diagnostics.used.map(_.toString.toLowerCase)

    System.gc()
    System.runFinalization()

    diagnostics match {
      case Vulnerable(attack, c, _) =>
        val complexity = c match {
          case Some(Exponential(_))   => Some("exponential")
          case Some(Polynomial(d, _)) => Some(s"$d polynomial")
          case None                   => None
        }
        Result(info, time, Status.Vulnerable, used, Some(attack.toString), complexity, None)
      case Safe(c, _) =>
        val complexity = c match {
          case Some(Constant) => Some("constant")
          case Some(Linear)   => Some("linear")
          case None           => None
        }
        Result(info, time, Status.Safe, used, None, complexity, None)
      case Unknown(ErrorKind.Timeout, _) =>
        Result(info, time, Status.Timeout, used, None, None, None)
      case Unknown(error, _) =>
        Result(info, time, Status.Error, used, None, None, Some(error.toString))
    }
  }
}
