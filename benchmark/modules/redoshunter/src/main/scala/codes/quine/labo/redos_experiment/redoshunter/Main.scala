package codes.quine.labo.redos_experiment.redoshunter

import cn.ac.ios.Bean.{AttackBean, ReDoSBean}
import cn.ac.ios.Patterns.NQ.PatternNQUtils
import cn.ac.ios.Patterns.POA.PatternPOAUtils
import cn.ac.ios.Patterns.EOA.PatternEOAUtils
import cn.ac.ios.Patterns.EOD.PatternEODUtils
import cn.ac.ios.Patterns.SLQ.PatternSLQUtils
import cn.ac.ios.Utils.Constant
import cn.ac.ios.Utils.FlagsUtils
import codes.quine.labo.redos_experiment.common.{Benchmarker, IO, RegExpInfo, Result, Status}
import com.monovore.decline.Opts
import io.circe.Encoder

import java.io.File
import java.util.concurrent.atomic.AtomicReference
import scala.concurrent.{Await, Future, TimeoutException, blocking}
import scala.sys.process.{Process, stderr}
import scala.jdk.CollectionConverters.CollectionHasAsScala
import scala.util.control.NonFatal
import scala.concurrent.ExecutionContext.Implicits._

object Main extends Benchmarker {
  def name: String = "redoshunter"
  def version: String = "82542c4"

  type Extra = Unit
  override def extraOpts: Opts[Unit] = Opts(())
  override def encodeExtra: Encoder[Unit] = Encoder.encodeUnit

  val DOT_REDOSHUNTER_DIR = ".redoshunter"
  val EXTENDED_COUNTING = Constant.EXTENDED_COUNTING.r

  def test(info: RegExpInfo, bench: Benchmarker.Config[Extra]): Result = {
    val regex = s"/${info.source}/${info.flags}"
    val language = "node"

    val start = System.nanoTime()
    val threadRef = new AtomicReference[Thread]()
    val future = Future(blocking {
      threadRef.set(Thread.currentThread())
      try {
        if (!EXTENDED_COUNTING.pattern.matcher(regex).find())
          Result(info, 0, Status.Safe, None, None, None)
        else {
          val bean = collectAttackBeans(regex, language)
          val attacks = attack(bean)

          attacks.find(_.isAttackSuccess) match {
            case Some(attack) =>
              Result(
                info,
                0,
                Status.Vulnerable,
                Some(attack.getType.name()),
                Some(attack.getVulnerabilityRegexSource),
                None
              )
            case None =>
              Result(info, 0, Status.Safe, None, None, None)
          }
        }
      } catch {
        case NonFatal(ex) =>
          ex.printStackTrace(stderr)
          Result(info, 0, Status.Error, None, None, None, Option(ex.getMessage))
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

  def collectAttackBeans(regex: String, language: String): ReDoSBean = {
    // Creates a bean to collect attack beans.
    val bean = new ReDoSBean
    bean.setRegex(regex)

    // Collects attack beans.
    val beanNQ = PatternNQUtils.getNQReDoSBean(regex, language)
    bean.getAttackBeanList.addAll(beanNQ.getAttackBeanList)
    val beanPOA = PatternPOAUtils.getPOAReDoSBean(regex, language)
    bean.getAttackBeanList.addAll(beanPOA.getAttackBeanList)
    val beanEOA = PatternEOAUtils.getEOAReDoSBean(regex, language)
    bean.getAttackBeanList.addAll(beanEOA.getAttackBeanList)
    val beanEOD = PatternEODUtils.getEODReDoSBean(regex, language)
    bean.getAttackBeanList.addAll(beanEOD.getAttackBeanList)
    val beanSLQ = PatternSLQUtils.getSLQReDoSBean(regex, language)
    bean.getAttackBeanList.addAll(beanSLQ.getAttackBeanList)

    bean
  }

  def attack(bean: ReDoSBean): Seq[AttackBean] = {
    if (bean.getAttackBeanList.isEmpty) return Seq.empty

    val lines = Seq.newBuilder[String]
    val regex = FlagsUtils.divideRegexByFlags(bean.getRegex)

    lines.addOne(regex.getRegex)
    lines.addOne(regex.getFlags)
    lines.addOne("s") // model

    for (attack <- bean.getAttackBeanList.asScala) {
      lines.addOne(attack.getType.name)
      lines.addOne(attack.getAttackStringFormatSp)
    }

    val attackTextFilename = s"${DOT_REDOSHUNTER_DIR}/js/js_attack_${System.currentTimeMillis()}.txt"
    val attackResultFilename = attackTextFilename.replace(".txt", "_result.txt")
    val attackJSFilename = s"${DOT_REDOSHUNTER_DIR}/js/attack.js"

    var process: Process = null
    try {
      IO.writeRaw(attackTextFilename, lines.result().mkString("", "\n", "\n"))

      process = Process(
        Seq(
          "node",
          attackJSFilename.replace(DOT_REDOSHUNTER_DIR, "."),
          attackTextFilename.replace(DOT_REDOSHUNTER_DIR, ".")
        ),
        cwd = new File(DOT_REDOSHUNTER_DIR)
      ).run()
      process.exitValue() // Waits for existing the process.
      val results = IO.readRaw(attackResultFilename).linesIterator
      for ((result, attack) <- results.zip(bean.getAttackBeanList.asScala)) {
        val Array(ok, repeatTimes, attackTime, _ @_*) = result.split("IOS_AC_CN")
        attack.setAttackSuccess(ok == "true")
        attack.setRepeatTimes(repeatTimes.toInt)
        attack.setAttackTime(attackTime.toInt)
      }

      bean.getAttackBeanList.asScala.toSeq
    } finally {
      Option(process).foreach(_.destroy())
      IO.delete(attackTextFilename)
      IO.delete(attackResultFilename)
    }
  }
}
