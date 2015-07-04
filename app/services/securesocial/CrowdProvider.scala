package services.securesocial

import models.UUID
import org.joda.time.DateTime
import play.api.Application
import play.api.Play.current
import play.api.libs.json.Json
import play.api.mvc._
import securesocial.controllers.ProviderController._
import securesocial.core._
import securesocial.core.providers.Token
import securesocial.core.providers.utils.{GravatarHelper, RoutesHelper}

/**
 * Provider for CROWD. This could be used with other services as well. See scripts/crowd/clowder.php for
 * the accompanying script.
 */
class CrowdProvider(application: Application) extends IdentityProvider(application) {
  override def id: String = CrowdProvider.crowd

  override def authMethod: AuthenticationMethod = CrowdProvider.authMethod

  override def fillProfile(user: SocialUser): SocialUser = user

  override def doAuth[A]()(implicit request: Request[A]): Either[Result, SocialUser] = {
    if (request.queryString.isEmpty) {
      // no info, so this is the start
      // - redirect to crowd url, passing token and this url for callback
      // - token has short lifetime and is atteched to client ip address
      val token = UUID.generate().stringify
      val userip = request.remoteAddress
      UserService.save(new Token(token, userip, DateTime.now(), DateTime.now().plusMinutes(CrowdProvider.crowdServerTimeOut), false))
      val query = Map[String, Seq[String]]("redirecturl" -> Seq[String](RoutesHelper.authenticate(id).absoluteURL()),
        "token" -> Seq[String](token))
      Left(Results.Redirect(CrowdProvider.crowdServerURL, query))
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
                  val user = new SocialUser(IdentityId((json \ "username").as[String], CrowdProvider.crowd),
                    (json \ "first-name").as[String], (json \ "last-name").as[String], (json \ "display-name").as[String],
                    Some(email), avatar, authMethod, None, None, None)
                  println(user)
                  Right(user)
                }
                case None => Left(Redirect(RoutesHelper.login()).flashing("error" -> "Invalid result"))
              }
            }
            case None => Left(Redirect(RoutesHelper.login()).flashing("error" -> "Invalid token"))
          }
        }
        case None => Left(Redirect(RoutesHelper.login()).flashing("error" -> "Invalid token"))
      }
    }
  }
}

object CrowdProvider {
  val crowd = "crowd"
  val authMethod = AuthenticationMethod("crowd")

  lazy val crowdServerURL = current.configuration.getString(CrowdServerURL).getOrElse("http://localhost/clowder.php")
  lazy val crowdServerTimeOut = current.configuration.getInt(CrowdServerTimeOut).getOrElse(5)

  // remote url
  private val CrowdServerURL = "securesocial.crowd.url"
  // time token is alive for
  private val CrowdServerTimeOut = "securesocial.crowd.timeout"
}