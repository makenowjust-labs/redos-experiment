package codes.quine.labo.redos_experiment.common

import io.circe.{Decoder, Encoder}
import io.circe.generic.semiauto.{deriveDecoder, deriveEncoder}

case class Result(
    info: RegExpInfo,
    time: Long,
    status: Status,
    used: Option[String] = None,
    attack: Option[String] = None,
    complexity: Option[String] = None,
    message: Option[String] = None
)

object Result {
  implicit def encode: Encoder[Result] = deriveEncoder
}
