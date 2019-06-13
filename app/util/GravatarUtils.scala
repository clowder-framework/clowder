package util

import java.security.MessageDigest
import concurrent.Await
import scala.concurrent.duration._
import play.api.Logger
import play.api.libs.ws.WS
import securesocial.core.providers.UsernamePasswordProvider

object GravatarUtils {

  // Start code from: securesocial.core.providers.utils.GravatarHelper. - Changing gravatar url to be https.
  def avatarFor(email: String): Option[String] = {
    if ( UsernamePasswordProvider.enableGravatar ) {
      hash(email).map( hash => {
        val GravatarUrl = "https://www.gravatar.com/avatar/%s?d=404"
        val url = GravatarUrl.format(hash)
        val promise = WS.url(url).get()
        try {
          val result = Await.result(promise, 10.seconds)
          if (result.status == 200) Some(url) else None
        } catch {
          case e: Exception => {
            Logger.error("[securesocial] error invoking gravatar", e)
            None
          }
        }
      }).getOrElse(None)
    } else {
      None
    }
  }

  private def hash(email: String): Option[String] = {
    val s = email.trim.toLowerCase
    if ( s.length > 0 ) {

      val out = MessageDigest.getInstance("MD5").digest(s.getBytes)
      Some(BigInt(1, out).toString(16))
    } else {
      None
    }
  }
  // End code from: securesocial.core.providers.utils.GravatarHelper.

}
