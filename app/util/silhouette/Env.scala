package util.silhouette.auth

import com.mohiva.play.silhouette.api.Env
import com.mohiva.play.silhouette.impl.authenticators.CookieAuthenticator
import models.User

/**
 * The default env.
 */
trait ClowderEnv extends Env {
  type I = User
  type A = CookieAuthenticator

  def authenticatorFromRequest(request) = {

  }
}
