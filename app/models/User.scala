package models

import play.api.Play.current
import java.security.MessageDigest
import play.api.Play.configuration

import securesocial.core._

/**
 * Simple class to capture basic User Information. This is similar to Identity in securesocial
 *
 * @author Rob Kooper
 */
trait User extends Identity {
  def id: UUID
  def profile: Option[Profile]
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
        if (url.contains("?")) {
          url+"&s="+size
        } else {
          url+"?s="+size
        }
      }
      case None => {
        val configuration = play.api.Play.configuration
        val default_gravatar = configuration.getString("default_gravatar").getOrElse("")
        val emailHash = getEmailHash()

    if (profile.isDefined && profile.get.avatarUrl.isDefined) {
      profile.get.avatarUrl.get
    } else if (avatarUrl.isDefined) {
      avatarUrl.get
    } else {
      s"http://www.gravatar.com/avatar/${getEmailHash}?s=${size}&d=${default_gravatar}"
    }
  }

  /**
   * @return lower case md5 hash of the user's email
   */
  def getEmailHash: String = {
    MessageDigest.getInstance("MD5")
      .digest(email.getOrElse("").getBytes("UTF-8"))
      .map("%02X".format(_))
      .mkString
      .toLowerCase
  }

  def getFollowedObjectList(objectType : String) : List[TypedID] = {
    followedEntities.filter { x => x.objectType == objectType }  
  }

  /**
  * return MiniUser constructed from the user model
  */
  def getMiniUser: MiniUser = {
    new MiniUser(id = id, fullName = fullName, avatarURL = getAvatarUrl(), email = email)
  }
}

object User {
  def anonymous = new ClowderUser(UUID("000000000000000000000000"),
  new IdentityId("anonymous", ""),
  "Anonymous", "User", "Anonymous User",
  None,
  AuthenticationMethod.UserPassword)
  // takes care of automatic conversion to/from JSON
  implicit val roleFormat = Json.format[Role]
  implicit val userSpaceAndRoleFormat = Json.format[UserSpaceAndRole]
  implicit val userFormat = Json.format[User]
}

case class ClowderUser(
  id: UUID = UUID.generate(),

  // securesocial identity
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

  // profile
  profile: Option[Profile] = None,

  // following
  followedEntities: List[TypedID] = List.empty,
  followers: List[UUID] = List.empty,
  friends: Option[List[String]] = None,

  // social
  viewed: Option[List[UUID]] = None
) extends User

case class Profile(
  avatarUrl: Option[String] = None,
  biography: Option[String] = None,
  currentprojects: List[String] = List.empty,
  institution: Option[String] = None,
  orcidID: Option[String] = None,
  pastprojects: List[String] = List.empty,
  position: Option[String] = None,
  emailsettings: Option[String] = None
) {
  /** return position at institution */
  def getPositionAtInstitution: String = {
    (position, institution) match {
      case (Some(p), Some(i)) => s"$p at $i"
      case (Some(p), None) => p
      case (None, Some(i)) => i
      case (None, None) => ""
    }
  }
}
