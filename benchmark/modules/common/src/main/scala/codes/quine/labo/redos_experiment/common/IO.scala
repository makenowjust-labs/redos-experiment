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
    parser.decode(Files.readString(Path.of(filename))).toTry.get

  def write[A: Encoder](filename: String, value: A): Unit =
    Files.writeString(Path.of(filename), Printer.noSpaces.copy(dropNullValues = true).print(value.asJson))
}
