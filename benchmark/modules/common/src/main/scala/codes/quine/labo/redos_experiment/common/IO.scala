package codes.quine.labo.redos_experiment.common

import java.nio.file.Files
import java.nio.file.Path

import io.circe.Decoder
import io.circe.Encoder
import io.circe.Printer
import io.circe.parser
import io.circe.syntax._

object IO {
  def read[A: Decoder](filename: String): A =
    parser.decode(readRaw(filename)).toTry.get

  def write[A: Encoder](filename: String, value: A): Unit =
    writeRaw(filename, Printer.noSpaces.copy(dropNullValues = true).print(value.asJson))

  def writeRaw(filename: String, content: String): Unit =
    Files.writeString(Path.of(filename), content)

  def readRaw(filename: String): String =
    Files.readString(Path.of(filename))

  def delete(filename: String): Unit =
    Files.deleteIfExists(Path.of(filename))
}
