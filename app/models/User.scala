package models


import java.security.MessageDigest
import java.util.Date

import com.mohiva.play.silhouette.api.{Authenticator, Identity, LoginInfo}
import _root_.services.DI
import com.mohiva.play.silhouette.api.services.AuthenticatorService
import com.mohiva.play.silhouette.impl.authenticators.DummyAuthenticator
import play.api.libs.json.Json
import scala.language.implicitConversions


object UserStatus extends Enumeration {
	  type UserStatus = Value
	  val Inactive, Active, Admin = Value
	}

/**
 * Simple class to capture basic User Information. This is similar to Identity in securesocial
 *
 */
trait User extends Identity {
  def id: UUID

  def firstName: Option[String]
  def lastName: Option[String]
  def fullName: Option[String]
  def email: Option[String]
  def avatarUrl: Option[String]
  def authMethod: Authenticator
  def providerId: String

  def status: UserStatus.Value
  def profile: Option[Profile]
  def friends: Option[List[String]]
  def followedEntities: List[String]
  def followers: List[UUID]
  def viewed: Option[List[UUID]]
  def spaceandrole: List[String]
  def repositoryPreferences: Map[String,Any]
  def termsOfServices: Option[UserTermsOfServices]
  def lastLogin: Option[Date]

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
    val configuration = DI.injector.getInstance(classOf[play.api.Configuration])
    val default_gravatar = configuration.get[String]("default_gravatar")

    if (profile.isDefined && profile.get.avatarUrl.isDefined) {
      profile.get.avatarUrl.get
    } else if (avatarUrl.isDefined) {
      avatarUrl.get
    } else {
      s"https://www.gravatar.com/avatar/${getEmailHash}?s=${size}&d=${default_gravatar}"
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

  /**
  * return MiniUser constructed from the user model
  */
  def getMiniUser: MiniUser = {
    new MiniUser(id = id, fullName = fullName, avatarURL = getAvatarUrl(), email = email)
  }

  override def toString: String = format(false)

  def format(paren: Boolean): String = {
    val e = email.fold(" ")(x => s""" <${x}> """)
    val x = (providerId) match {
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

///** This object used to be available in a previous version of Secure Social. To minimize the code changes we are creating
// * a version here instead of just adding `providerId` and `userId` as individual entries in `User` */
//case class IdentityId(providerId: String, userId: String) extends UserProfile

object User {
  def anonymous = new ClowderUser(UUID("000000000000000000000000"),
    providerId = "",
    userId = "anonymous",
    firstName= Some("Anonymous"),
    lastName= Some("User"),
    fullName= Some("Anonymous User"),
    email=None,
    authMethod=new DummyAuthenticator(new LoginInfo("","")),
    status=UserStatus.Admin,
    termsOfServices=Some(UserTermsOfServices(accepted=true, acceptedDate=new Date(), "")))
  implicit def userToMiniUser(x: User): MiniUser = x.getMiniUser
}

case class MiniUser(
   id: UUID,
   fullName: Option[String],
   avatarURL: String,
   email: Option[String])

case class ClowderUser (
  id: UUID = UUID.generate(),
  providerId: String,
  userId: String,
  firstName: Option[String],
  lastName: Option[String],
  fullName: Option[String],
  email: Option[String],
  authMethod: Authenticator,
  avatarUrl: Option[String] = None,

  // should user be active
  status: UserStatus.Value = UserStatus.Inactive,
  
  // has the user escalated privileges, this is never saved to the database
  @transient superAdminMode: Boolean = false,

  // profile
  profile: Option[Profile] = None,

  // following
  followedEntities: List[String] = List.empty,
  followers: List[UUID] = List.empty,
  friends: Option[List[String]] = None,

  // social
  viewed: Option[List[UUID]] = None,

  // spaces
  spaceandrole: List[String] = List.empty,

  //staging area
  repositoryPreferences: Map[String,Any] = Map.empty,

  // terms of service
  termsOfServices: Option[UserTermsOfServices] = None,

  lastLogin: Option[Date] = None
  
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
  providerId: String,
  userId: String
)

/** Legacy class previously used by SecureSocial */
case class IdentityId(providerId: String, userId: String)

object UserApiKey {
  implicit val userApiKeyFormat = Json.format[UserApiKey]
}
