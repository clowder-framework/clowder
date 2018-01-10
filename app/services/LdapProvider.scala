package services

import models.UUID
import javax.net.ssl.SSLSocketFactory
import org.joda.time.DateTime
import play.api.Application
import play.api.Play.current
import play.api.libs.json.Json
import play.api.mvc._
import securesocial.controllers.ProviderController._
import securesocial.core._
import securesocial.core.providers.Token
import securesocial.core.providers.utils.{GravatarHelper, RoutesHelper}
import com.unboundid.ldap.sdk._
import com.unboundid.util.ssl.{JVMDefaultTrustManager, SSLUtil, TrustAllTrustManager}

class LdapProvider(application: Application) extends IdentityProvider(application) {
  override def id: String = LdapProvider.ldap

  override def authMethod: AuthenticationMethod = LdapProvider.authMethod

  override def fillProfile(user: SocialUser): SocialUser = user

  override def doAuth[A]()(implicit request: Request[A]): Either[Result, SocialUser] = {
    if (request.queryString.isEmpty) {
      // no info, so this is the start
      // - redirect to crowd url, passing token and this url for callback
      // - token has short lifetime and is atteched to client ip address
      val token = UUID.generate().stringify
      val userip = request.remoteAddress
      UserService.save(new Token(token, userip, DateTime.now(), DateTime.now().plusMinutes(LdapProvider.ldapServerTimeOut), false))
      val query = Map[String, Seq[String]]("redirecturl" -> Seq[String](RoutesHelper.authenticate(id).absoluteURL()),
        "token" -> Seq[String](token))
      Left(Results.Redirect(LdapProvider.ldapServerURL, query))
    } else {
      // response from script a few things first
      // 1) check to make sure token is returned
      // 2) make sure token exists, is valid, and has same client ip address
      // 3) parse user and login as user
      request.getQueryString("token") match {
        case Some(qt) => {
          UserService.findToken(qt) match {
            case Some(ut) => {
              UserService.deleteToken(qt)
              if (ut.email != request.remoteAddress) return Left(Redirect(RoutesHelper.login()).flashing("error" -> "Invalid token"))
              if (ut.isExpired) return Left(Redirect(RoutesHelper.login()).flashing("error" -> "Token expired"))
              request.getQueryString("user") match {
                case Some(x) => {
                  val json = Json.parse(x)
                  val email = (json \ "email").as[String]
                  val avatar = GravatarHelper.avatarFor(email)
                  val user = new SocialUser(IdentityId((json \ "userId").as[String], LdapProvider.ldap),
                    (json \ "firstName").as[String], (json \ "lastName").as[String], (json \ "fullName").as[String],
                    Some(email), avatar, authMethod, None, None, None)
                  Right(user)
                }
                case None => Left(Redirect(RoutesHelper.login()).flashing("error" -> "Invalid user"))
              }
            }
            case None => Left(Redirect(RoutesHelper.login()).flashing("error" -> "Invalid token"))
          }
        }
        case None => Left(Redirect(RoutesHelper.login()).flashing("error" -> "Token not found"))
      }
    }
  }
}

object LdapProvider {
  val ldap = "ldap"
  val authMethod = AuthenticationMethod("ldap")

  // Get LDAP configuration details
  private val conf = current.configuration
  lazy val hostname = conf.getString("securesocial.ldap.hostname").getOrElse("ldap.example.com")
  lazy val group = conf.getString("securesocial.ldap.group").getOrElse("test")
  lazy val port = conf.getInt("securesocial.ldap.port").getOrElse(636)
  lazy val baseDN = conf.getString("securesocial.ldap.baseDN").getOrElse("dc=example,dc=com")
  lazy val trustAllCertificates = conf.getBoolean("securesocial.ldap.trustAllCertificates").getOrElse(false)
  lazy val baseUserNamespace = conf.getString("securesocial.ldap.userDN").getOrElse("ou=people") + "," + baseDN
  lazy val baseGroupNamespace = conf.getString("securesocial.ldap.groupDN").getOrElse("ou=groups") + "," + baseDN
  lazy val objectClass = conf.getString("securesocial.ldap.objectClass").getOrElse("inetorgperson")
  lazy val ldapServerURL = conf.getString("securesocial.ldap.url").getOrElse("http://localhost/ldap")
  lazy val ldapServerTimeOut = conf.getInt("securesocial.ldap.timeout").getOrElse(5)
}