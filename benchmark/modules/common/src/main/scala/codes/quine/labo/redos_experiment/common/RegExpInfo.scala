package codes.quine.labo.redos_experiment.common

import io.circe.Decoder
import io.circe.Encoder
import io.circe.generic.semiauto.deriveDecoder
import io.circe.generic.semiauto.deriveEncoder

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
  implicit def decodeRegExpInfo: Decoder[RegExpInfo] = deriveDecoder[RegExpInfo]
  implicit def encodeRegExpInfo: Encoder[RegExpInfo] = deriveEncoder[RegExpInfo]
}
