package redos_experiment

import java.io.BufferedWriter
import java.io.StringWriter

import scala.jdk.CollectionConverters.*

import redos.regex.Analyzer
import redos.regex.Pattern

object Main extends Wrapper {

  // The length of an attack string and the number of the matching steps to take it as an attack are the ones of
  // `RedosTester.testSingleRegex`, which has them as local variables.
  val maxLength = 128
  val threshold = 1e5

  def check(source: String, flags: String): ujson.Obj = {
    val analyzer = new Analyzer(Pattern.compile(source), maxLength)
    analyzer.doStaticAnalysis()
    val output = new StringWriter()
    val writer = new BufferedWriter(output)
    analyzer.doDynamicAnalysis(writer, -1, threshold)
    writer.flush()
    if (!analyzer.isVulnerable) safe
    else {
      // The output is the pattern, the line of the complexity, and the line of the attack string in JSON.
      // The lines are taken from the end, because the pattern can have line breaks.
      val lines = output.toString.split("\n")
      val complexity = lines(lines.length - 2) match {
        case "Find vulnerability (Exponential) in structure!" => exponential
        case "Find vulnerability (Polynomial) in structure!"  => ujson.Obj("type" -> "polynomial")
      }
      val attack = ujson.read(lines.last)
      val result =
        vulnerable(complexity, pumps(Seq(attack("prefix").str), Seq(attack("pump").str), attack("suffix").str))
      // The analysis stops at the first structure whose attack string is valid, so it is the last one with a string.
      val structures = field(analyzer, "possibleVuls").asInstanceOf[java.util.List[AnyRef]].asScala
      val found = structures.filter(field(_, "result") == Analyzer.Existance.EXIST).last
      result("pattern") = field(found, "type").toString
      result
    }
  }

  def field(target: AnyRef, name: String): AnyRef = {
    val field = target.getClass.getDeclaredField(name)
    field.setAccessible(true)
    field.get(target)
  }
}
