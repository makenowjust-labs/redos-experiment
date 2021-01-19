package codes.quine.labo.redos_experiment.common

import io.circe.{Decoder, Encoder}
import io.circe.generic.semiauto.{deriveDecoder, deriveEncoder}

final case class RegExpInfo(
    `package`: String,
    version: String,
    path: String,
    line: Int,
    column: Int,
    source: String,
    flags: String
) {
  override def toString: String = s"/$source/$flags (at $path:$line:$column in ${`package`}@$version)"
}

object RegExpInfo {
  implicit def decode: Decoder[RegExpInfo] = deriveDecoder[RegExpInfo]
  implicit def encode: Encoder[RegExpInfo] = deriveEncoder[RegExpInfo]
}
