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

/**
 * Administration pages.
 * 
 * @author Luigi Marini
 *
 */
object Admin extends SecuredController {
  
  def main = SecuredAction(authorization=WithPermission(Permission.Admin)) { request =>
    Ok(views.html.admin())
  }
  
  def reindexFiles = SecuredAction(parse.json, authorization=WithPermission(Permission.AddIndex)) { request =>
    Ok("Reindexing")
  }
  
  def test = SecuredAction(parse.json, authorization=WithPermission(Permission.Public)) { request =>
    Ok("""{"message":"test"}""").as(JSON)
  }
  
  def secureTest = SecuredAction(parse.json, authorization=WithPermission(Permission.Admin)) { request =>
    Ok("""{"message":"secure test"}""").as(JSON)
  }
}