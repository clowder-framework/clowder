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
  avatarUrl: Option[String] = None,
  biography: Option[String] = None,
  currentprojects: Option[String] = None,
  institution: Option[String] = None,
  orcidID: Option[String] = None,
  pastprojects: Option[String] = None,
  position: Option[String] = None,
  friends: Option[List[String]] = None,
  viewed: Option[List[UUID]] = None,
  followedFiles: List[String] = List.empty,
  followedDatasets: List[String] = List.empty,
  followsUsers: List[String] = List.empty,
  followedByUsers: List[String] = List.empty
)

case class Info(
  avatarUrl: Option[String],
  biography: Option[String],
  currentprojects: Option[String],
  institution: Option[String],
  orcidID: Option[String] = None,
  pastprojects: Option[String],
  position: Option[String]
)

object User {
  // takes care of automatic conversion to/from JSON
  implicit val userFormat = Json.format[User]
}
