package models

import play.api.Play.current
import java.security.MessageDigest
import java.util.Date

import play.api.Play.configuration
import play.api.libs.json.{JsObject, Json, Writes}
import securesocial.core._
import services.AppConfiguration

/**
 * Simple class to capture basic User Information. This is similar to Identity in securesocial
 *
 */
trait User extends Identity {
  def id: UUID
  def active: Boolean
  def serverAdmin: Boolean
  def profile: Option[Profile]
  def friends: Option[List[String]]
  def followedEntities: List[TypedID]
  def followers: List[UUID]
  def viewed: Option[List[UUID]]
  def spaceandrole: List[UserSpaceAndRole]
  def repositoryPreferences: Map[String,Any]
  def termsOfServices: Option[UserTermsOfServices]

  // One can only be superAdmin iff you are a serveradmin
  def superAdminMode: Boolean

  /**
   * Get the avatar URL for this user's profile
   * If user has no avatar URL, this will return a unique URL based on
   * the hash of this user's email address. Gravatar provide an image
   * as specified in application.conf
   *
   * @return Full gravatar URL for the user's profile picture
   */
  def getAvatarUrl(size: Integer = 256): String = {
    val default_gravatar = configuration.getString("default_gravatar").getOrElse("")

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

  override def toString: String = format(false)

  def format(paren: Boolean): String = {
    val e = email.fold(" ")(x => s""" <${x}> """)
    val x = (identityId.providerId) match {
      case ("userpass") => s"""${fullName}${e}[Local Account]"""
      case (provider) => s"""${fullName}${e}[${provider.capitalize}]"""
    }
    if (paren) {
      x.replaceAll("<", "(").replaceAll(">", ")")
    } else {
      x
    }
  }
}

object User {
  def anonymous = new ClowderUser(UUID("000000000000000000000000"),
    new IdentityId("anonymous", ""),
    firstName="Anonymous",
    lastName="User",
    fullName="Anonymous User",
    email=None,
    authMethod=AuthenticationMethod("SystemUser"),
    active=true,
    serverAdmin=true,
    termsOfServices=Some(UserTermsOfServices(accepted=true, acceptedDate=new Date(), "")))
  implicit def userToMiniUser(x: User): MiniUser = x.getMiniUser
}

case class MiniUser(
   id: UUID,
   fullName: String,
   avatarURL: String,
   email: Option[String])

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

  // should user be active
  active: Boolean = false,

  // is the user an admin
  serverAdmin: Boolean = false,

  // has the user escalated privileges, this is never saved to the database
  @transient superAdminMode: Boolean = false,

  // profile
  profile: Option[Profile] = None,

  // following
  followedEntities: List[TypedID] = List.empty,
  followers: List[UUID] = List.empty,
  friends: Option[List[String]] = None,

  // social
  viewed: Option[List[UUID]] = None,

  // spaces
  spaceandrole: List[UserSpaceAndRole] = List.empty,

  //staging area
  repositoryPreferences: Map[String,Any] = Map.empty,

  // terms of service
  termsOfServices: Option[UserTermsOfServices] = None

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

case class UserTermsOfServices(
  accepted: Boolean = false,
  acceptedDate: Date = null,
  acceptedVersion: String = ""
)

case class UserApiKey(
  name: String,
  key: String,
  identityId: IdentityId
)

object UserApiKey {
  implicit val identityIdFormat = Json.format[IdentityId]
  implicit val userApiKeyFormat = Json.format[UserApiKey]
}
