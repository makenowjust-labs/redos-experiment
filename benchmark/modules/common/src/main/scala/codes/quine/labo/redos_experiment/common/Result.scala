package codes.quine.labo.redos_experiment.common

import io.circe.Encoder
import io.circe.generic.semiauto.deriveEncoder

final case class Result(
    info: RegExpInfo,
    time: Long,
    status: Status,
    checker: Option[String] = None,
    attack: Option[String] = None,
    complexity: Option[String] = None,
    message: Option[String] = None
) {
  override def toString: String = {
    val seq = Seq.newBuilder[String]
    seq += s"Status:         $status"
    seq += s"Execution time: ${time / 1e9} s"
    checker.foreach(s => seq += s"Checker       : $s")
    // Comments out the below line for avoiding a large output.
    // attack.foreach(s => seq += s"Attack string : $s")
    complexity.foreach(s => seq += s"Complexity    : $s")
    message.foreach(s => seq += s"Message       : $s")
    seq.result().mkString("\n")
  }
}

object Result {
  implicit def encodeResult: Encoder[Result] = deriveEncoder
}
