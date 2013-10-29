/**
 *
 */
package controllers

import play.api.mvc.Controller
import play.api.mvc.Action
import play.api.Routes
import securesocial.core.SecureSocial._
import securesocial.core.SecureSocial
import api.ApiController
import api.WithPermission
import api.Permission
import models.AppConfiguration
import play.api.libs.json.Json
import play.api.libs.json.Json._
import play.api.Logger

/**
 * Administration pages.
 *
 * @author Luigi Marini
 *
 */
object Admin extends SecuredController {

  private val themes = "bootstrap/bootstrap.css" ::
    "bootstrap-amelia.min.css" ::
    "bootstrap-simplex.min.css" :: Nil

  def main = SecuredAction(authorization = WithPermission(Permission.Admin)) { request =>
    val themeId = themes.indexOf(getTheme)
    Logger.debug("Theme id " + themeId)
    Ok(views.html.admin(themeId))
  }

  def reindexFiles = SecuredAction(parse.json, authorization = WithPermission(Permission.AddIndex)) { request =>
    Ok("Reindexing")
  }

  def test = SecuredAction(parse.json, authorization = WithPermission(Permission.Public)) { request =>
    Ok("""{"message":"test"}""").as(JSON)
  }

  def secureTest = SecuredAction(parse.json, authorization = WithPermission(Permission.Admin)) { request =>
    Ok("""{"message":"secure test"}""").as(JSON)
  }

  def setTheme() = SecuredAction(parse.json, authorization = WithPermission(Permission.Admin)) { implicit request =>
    request.body.\("theme").asOpt[Int] match {
      case Some(theme) => {
        AppConfiguration.setTheme(themes(theme))
        Ok("""{"status":"ok"}""").as(JSON)
      }
      case None => {
        Logger.error("no theme specified")
        BadRequest
      }
    }
  }

  def getTheme(): String = {
    AppConfiguration.getDefault match {
      case Some(appConf) => Logger.debug("Theme" + appConf.theme); appConf.theme
      case None => themes(0)
    }
  }
}