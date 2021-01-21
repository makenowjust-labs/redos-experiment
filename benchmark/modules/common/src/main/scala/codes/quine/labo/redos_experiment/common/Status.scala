package codes.quine.labo.redos_experiment.common

import io.circe.Encoder

sealed abstract class Status extends Product with Serializable

object Status {
  implicit def encodeStatus: Encoder[Status] = Encoder.encodeString.contramap(_.toString)

  case object Safe extends Status {
    override def toString: String = "safe"
  }

  case object Vulnerable extends Status {
    override def toString: String = "vulnerable"
  }

  case object Timeout extends Status {
    override def toString: String = "timeout"
  }

  case object Error extends Status {
    override def toString: String = "error"
  }
}
