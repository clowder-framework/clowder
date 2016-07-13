package services

import play.api.libs.ws.Response
import play.api.{Application, Logger}
import securesocial.core._

/**
  * ORCID provider
  */
class ORCIDProvider(application: Application) extends OAuth2Provider(application) {
  override def id = ORCIDProvider.ORCID

  override protected def buildInfo(response: Response): OAuth2Info = {
    val json = response.json
    if ( Logger.isDebugEnabled ) {
      Logger.debug("[securesocial] got json back [" + json + "]")
    }
    // hack to store some information in the accesstoken that will be used later
    // in the fillProfile function. Orcid does not have an easy way to get user
    // information withouth the orcid id.
    val accessToken = (json \ OAuth2Constants.AccessToken).as[String] + " " +
      (json \ "orcid").as[String] + " " + (json \ "name").as[String]
    OAuth2Info(
      accessToken,
      (json \ OAuth2Constants.TokenType).asOpt[String],
      (json \ OAuth2Constants.ExpiresIn).asOpt[Int],
      (json \ OAuth2Constants.RefreshToken).asOpt[String]
    )
  }

   /**
    * Splits the accessToken and fills in the profile based on this information
    * a and will fix the accessToken to the correct values.
    */
  def fillProfile(user: SocialUser): SocialUser = {
    user.oAuth2Info match {
      case Some(auth2Info) => {
        auth2Info.accessToken.split(" ", 3) match {
          case Array(accessToken, identityId, fullName) => {
            val name = fullName.split(" ")
            val firstName = name.headOption.getOrElse("")
            val lastName = if (name.length > 1) {
              name.lastOption.getOrElse("")
            } else {
              ""
            }
            val newoauth2 = OAuth2Info(accessToken, auth2Info.tokenType, auth2Info.expiresIn, auth2Info.refreshToken)
            user.copy(
              identityId = IdentityId(identityId, id),
              firstName = firstName,
              lastName = lastName,
              fullName = fullName,
              oAuth2Info = Some(newoauth2)
            )
          }
          case _ => {
            Logger.error("Wrong info in oAuth2Info")
            throw new AuthenticationException()
          }
        }
      }
      case None => {
        Logger.error("Missing oAuth2Info")
        throw new AuthenticationException()
      }
    }
  }
}

object ORCIDProvider {
  val ORCID = "orcid"
}
