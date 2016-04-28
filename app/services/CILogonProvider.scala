package services

import play.api.libs.ws.WS
import play.api.{Application, Logger}
import play.api.libs.json.JsObject
import securesocial.core._


/**
 * A CILogon OAuth2 Provider
 */
class CILogonProvider(application: Application) extends OAuth2Provider(application) {
  val Error = "error"
  val Message = "message"
  val Type = "type"
  val Sub = "sub"
  val Name = "name"
  val GivenName = "given_name"
  val FamilyName = "family_name"
  val Picture = "picture"
  val Email = "email"


  override def id = CILogonProvider.CILogon

  def fillProfile(user: SocialUser): SocialUser = {
    val UserInfoApi = loadProperty("userinfoUrl").getOrElse(throwMissingPropertiesException())
    val accessToken = user.oAuth2Info.get.accessToken
    val promise = WS.url(UserInfoApi + accessToken).get()

    try {
      val response = awaitResult(promise)
      val me = response.json
      Logger.debug("Got back from CILogon : " + me.toString())
      (me \ Error).asOpt[JsObject] match {
        case Some(error) =>
          val message = (error \ Message).as[String]
          val errorType = ( error \ Type).as[String]
          Logger.error("[securesocial] error retrieving profile information from CILogon. Error type = %s, message = %s"
            .format(errorType,message))
          throw new AuthenticationException()
        case _ =>
          val userId = (me \ Sub).as[String]
          val firstName = (me \ GivenName).asOpt[String]
          val lastName = (me \ FamilyName).asOpt[String]
          val fullName = (me \ Name).asOpt[String]
          val avatarUrl = ( me \ Picture).asOpt[String]
          val email = ( me \ Email).asOpt[String]
          user.copy(
            identityId = IdentityId(userId, id),
            firstName = firstName.getOrElse(""),
            lastName = lastName.getOrElse(""),
            fullName = fullName.getOrElse(""),
            avatarUrl = avatarUrl,
            email = email
          )
      }
    } catch {
      case e: Exception => {
        Logger.error( "[securesocial] error retrieving profile information from CILogon", e)
        throw new AuthenticationException()
      }
    }
  }
}

object CILogonProvider {
  val CILogon = "cilogon"
}
