package models

import play.api.libs.json._
import play.api.Play.current
import java.security.MessageDigest

import securesocial.core._

/**
 * Simple class to capture basic User Information. This is similar to Identity in securesocial
 *
 * @author Rob Kooper
 */
trait User extends Identity {
  def id: UUID
  def biography: Option[String]
  def currentprojects: List[String]
  def institution: Option[String]
  def orcidID: Option[String]
  def pastprojects: List[String]
  def position: Option[String]
  def friends: Option[List[String]]
  def followedEntities: List[TypedID]
  def followers: List[UUID]
  def viewed: Option[List[UUID]]

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

  /**
   * @return string containing the current projects separated by commas
   */
  def getCurrentProjectsString(): String = {
    currentprojects.reduceLeft { (proj, next) =>
      proj + ", " + next
    }
  }

  /**
   * @return string containing the past projects separated by commas
   */
  def getPastProjectsString(): String = {
    pastprojects.reduceLeft { (proj, next) =>
      proj + ", " + next
    }
  }

  /**
  * return MiniUser constructed from the user model
  */
  def getMiniUser: MiniUser = {
    new MiniUser(id = id, fullName = fullName, avatarURL = getAvatarUrl, email = email)
  }

}


case class MediciUser(
  id: UUID = UUID.generate(),
  identityId: IdentityId,
  firstName: String,
  lastName: String,
  fullName: String,
  email: Option[String],
  authMethod: AuthenticationMethod,
  avatarUrl: Option[String] = None,
  oAuth1Info: Option[OAuth1Info] = None,
  oAuth2Info: Option[OAuth2Info] = None,
  passwordInfo: Option[PasswordInfo] = None,
  biography: Option[String] = None,
  currentprojects: List[String] = List.empty,
  institution: Option[String] = None,
  orcidID: Option[String] = None,
  pastprojects: List[String] = List.empty,
  position: Option[String] = None,
  friends: Option[List[String]] = None,
  followedEntities: List[TypedID] = List.empty,
  followers: List[UUID] = List.empty,
  viewed: Option[List[UUID]] = None
  ) extends User

case class Info(
  avatarUrl: Option[String],
  biography: Option[String],
  currentprojects: List[String],
  institution: Option[String],
  orcidID: Option[String] = None,
  pastprojects: List[String],
  position: Option[String],
  emailsettings: Option[String] = None
)
