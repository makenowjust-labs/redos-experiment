package redos_experiment

import java.io.BufferedReader
import java.io.FileDescriptor
import java.io.FileOutputStream
import java.io.InputStreamReader
import java.io.PrintStream
import java.nio.charset.StandardCharsets.UTF_8

/** Wrapper is the base of the wrapper of a tool on the JVM.
  *
  * It reads a request `{"source": ..., "flags": ...}` on each line of the standard input, and writes the result of
  * `check` on a line of the standard output.
  * What the tool prints to the standard output goes to the standard error instead.
  */
abstract class Wrapper {

  def check(source: String, flags: String): ujson.Obj

  def safe: ujson.Obj = ujson.Obj("status" -> "safe")

  def vulnerable(complexity: ujson.Value, attack: ujson.Value): ujson.Obj =
    ujson.Obj("status" -> "vulnerable", "complexity" -> complexity, "attack" -> attack)

  def error(message: String): ujson.Obj = ujson.Obj("status" -> "error", "message" -> message)

  def exponential: ujson.Value = ujson.Obj("type" -> "exponential")

  def polynomial(degree: Int): ujson.Value = ujson.Obj("type" -> "polynomial", "degree" -> degree)

  def pumps(prefixes: Seq[String], pumps: Seq[String], suffix: String): ujson.Value =
    ujson.Obj(
      "pumps" -> prefixes.zip(pumps).map { case (prefix, pump) => ujson.Obj("prefix" -> prefix, "pump" -> pump) },
      "suffix" -> suffix
    )

  def main(args: Array[String]): Unit = {
    val out = new PrintStream(new FileOutputStream(FileDescriptor.out), true, "UTF-8")
    System.setOut(System.err)
    val in = new BufferedReader(new InputStreamReader(System.in, UTF_8))

    out.println(ujson.write(ujson.Obj("ready" -> true)))
    var line = in.readLine()
    while (line != null) {
      val request = ujson.read(line)
      val result =
        try check(request("source").str, request("flags").str)
        catch {
          case ex: Exception          => error(ex.toString)
          case ex: StackOverflowError => error(ex.toString)
        }
      out.println(ujson.write(result, escapeUnicode = true))
      line = in.readLine()
    }
  }
}
