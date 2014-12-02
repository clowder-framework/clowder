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
  biography: Option[String] = None,
  personal: Option[String] = None,
  viewed: Option[List[UUID]] = None,
  number: Option[String] = None,
  building: Option[String] = None,
  room: Option[String] = None,
  currentprojects: Option[String] = None,
  pastprojects: Option[String] = None,
  avatarUrl : Option[String] = None,
  friends: Option[List[String]] = None
)

case class Info(bio: Option[String], personal: Option[String], number: Option[String], building: Option[String], room: Option[String], currentprojects: Option[String], pastprojects: Option[String])

object User {
  // takes care of automatic conversion to/from JSON
  implicit val userFormat = Json.format[User]
}
