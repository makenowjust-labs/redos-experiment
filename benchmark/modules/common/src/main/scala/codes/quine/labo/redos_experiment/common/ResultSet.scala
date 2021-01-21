package codes.quine.labo.redos_experiment.common

import java.time.ZonedDateTime

import io.circe.Encoder
import io.circe.generic.semiauto.deriveEncoder

final case class ResultSet[A](
    name: String,
    version: String,
    startTime: ZonedDateTime,
    endTime: ZonedDateTime,
    config: Benchmarker.Config[A],
    results: Seq[Result]
)

object ResultSet {
  implicit def encodeResultSet[A: Encoder]: Encoder[ResultSet[A]] = deriveEncoder
}
