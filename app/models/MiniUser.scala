package models

import play.api.libs.json._

/**
 * Class to contain a subset of User data for fast loading.
 *
 * @author Will Hennessy
 */

 case class MiniUser(
  id: UUID,
  fullName: String,
  avatarURL: Option[String])

object MiniUser {
 implicit val miniUserFormat = Json.format[MiniUser]
}