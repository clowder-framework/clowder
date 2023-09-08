package services

import play.api.libs.ws.WS
import play.api.{Application, Logger}
import play.api.libs.json.JsObject
import securesocial.core._
import scala.collection.JavaConverters._


/**
 * A Keycloak OAuth2 Provider
 */
class KeycloakProvider(application: Application) extends OAuth2Provider(application) {
  val Error = "error"
  val Message = "message"
  val Type = "type"
  val Sub = "sub"
  val Name = "name"
  val GivenName = "given_name"
  val FamilyName = "family_name"
  //  todo: picture wont work
  val Picture = "picture"
  val Email = "email"
  val Groups = "groups"

  override def id = KeycloakProvider.Keycloak

  def fillProfile(user: SocialUser): SocialUser = {
    val UserInfoApi = loadProperty("userinfoUrl").getOrElse(throwMissingPropertiesException())
    val accessToken = user.oAuth2Info.get.accessToken
    val promise = WS.url(UserInfoApi.toString).withHeaders(("Authorization", "Bearer " + accessToken)).get()

    try {
      val response = awaitResult(promise)
      val me = response.json
      Logger.debug("Got back from Keycloak : " + me.toString())
      (me \ Error).asOpt[JsObject] match {
        case Some(error) =>
          val message = (error \ Message).as[String]
          val errorType = ( error \ Type).as[String]
          Logger.error("[securesocial] error retrieving profile information from Keycloak. Error type = %s, message = %s"
            .format(errorType,message))
          throw new AuthenticationException()
        case _ =>
          val userId = (me \ Sub).as[String]
          val firstName = (me \ GivenName).asOpt[String]
          val lastName = (me \ FamilyName).asOpt[String]
          val fullName = (me \ Name).asOpt[String]
          val avatarUrl = ( me \ Picture).asOpt[String]
          val email = ( me \ Email).asOpt[String]
          val groups = ( me \ Groups).asOpt[List[String]]
          val roles = ( me \ "resource_access" \ "account" \ "roles").asOpt[List[String]]
          (application.configuration.getList("securesocial.keycloak.groups"), groups) match {
            case (Some(conf), Some(keycloak)) => {
              val conflist = conf.unwrapped().asScala.toList
              if (keycloak.intersect(conflist).isEmpty) {
                throw new AuthenticationException()
              }
            }
            case (Some(_), None) => throw new AuthenticationException()
            case (None, _) => Logger.debug("[securesocial] No check needed for groups")
          }
          (application.configuration.getList("securesocial.keycloak.roles"), roles) match {
            case (Some(conf), Some(keycloak)) => {
              val conflist = conf.unwrapped().asScala.toList
              if (keycloak.intersect(conflist).isEmpty) {
                throw new AuthenticationException()
              }
            }
            case (Some(_), None) => throw new AuthenticationException()
            case (None, _) => Logger.debug("[securesocial] No check needed for roles")
          }
          user.copy(
            identityId = IdentityId(userId, id),
            firstName = firstName.getOrElse(""),
            lastName = lastName.getOrElse(""),
            fullName = fullName.getOrElse({
              if (firstName.isDefined && lastName.isDefined) {
                firstName.get + " " + lastName.get
              } else if (firstName.isDefined) {
                firstName.get
              } else if (lastName.isDefined) {
                lastName.get
              } else {
                ""
              }
            }),
            avatarUrl = avatarUrl,
            email = email
          )
      }
    } catch {
      case e: Exception => {
        Logger.error( "[securesocial] error retrieving profile information from Keycloak", e)
        throw new AuthenticationException()
      }
    }
  }
}

object KeycloakProvider {
  val Keycloak = "keycloak"
}
