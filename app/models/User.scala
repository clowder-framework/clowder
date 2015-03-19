package models

import play.api.libs.json._
import play.api.Play.current
import java.security.MessageDigest

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
  email: Option[String],
  avatarUrl: Option[String] = None,
  biography: Option[String] = None,
  currentprojects: Option[String] = None,
  institution: Option[String] = None,
  orcidID: Option[String] = None,
  pastprojects: Option[String] = None,
  position: Option[String] = None,
  viewed: Option[List[UUID]] = None,
  followedFiles: List[UUID] = List.empty,
  followedDatasets: List[UUID] = List.empty,
  followedCollections: List[UUID] = List.empty,
  followedUsers: List[UUID] = List.empty,
  followers: List[UUID] = List.empty) {

  /**
   * Get the avatar URL for this user's profile
   * If user has no avatar URL, this will return a unique URL based on
   * the hash of this user's email address. Gravatar provide an image
   * as specified in application.conf
   * 
   * @return Full gravatar URL for the user's profile picture
   */
  def getAvatarUrl: String = {
    val size = "256"
    avatarUrl match {
      case Some(url) => {
        url+"?s="+size
      }
      case None => {
        val configuration = play.api.Play.configuration
        val default_gravatar = configuration.getString("default_gravatar").getOrElse("")
        val emailHash = getEmailHash()

        "http://www.gravatar.com/avatar/"+
          emailHash+
          "?s="+size+
          "&d="+default_gravatar
      }
    }
  }

  /**
   * @return lower case md5 hash of the user's email
   */
  def getEmailHash(): String = {
    MessageDigest.getInstance("MD5")
      .digest(email.getOrElse("").getBytes("UTF-8"))
      .map("%02X".format(_))
      .mkString
      .toLowerCase
  }
}

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
