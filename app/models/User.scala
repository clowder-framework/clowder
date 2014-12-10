package models

import play.api.libs.json._

/**
 * Simple class to capture basic User Information. This is similar to Identity in securesocial
 *
 * @author Rob Kooper
 */
case class User(
  id: UUID = UUID.generate(),
  firstName: String,
  lastName: String,
  fullName: String,
  email: String,
  avatarUrl : Option[String],
  friends: Option[List[UUID]] = None
)

object User {
  // takes care of automatic conversion to/from JSON
  implicit val userFormat = Json.format[User]
}
