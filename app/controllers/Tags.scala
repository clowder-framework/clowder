package controllers

import play.api.mvc.Controller
import securesocial.core.SecureSocial
import play.api.Logger
import play.api.libs.json.Json._
import models.Dataset
import play.api.mvc.Action
import models.File
import models.FileDAO
import api.WithPermission
import api.Permission
import services.ElasticsearchPlugin
import scala.util.parsing.json.JSONArray

/**
 * Tagging.
 * 
 * @author Luigi Marini
 */
object Tags extends SecuredController {

  def search(tag: String) = SecuredAction(parse.anyContent, authorization=WithPermission(Permission.SearchDatasets)) { implicit request =>
    val datasets = Dataset.findByTag(tag)
    val files = FileDAO.findByTag(tag)
    Ok(views.html.searchByTag(tag, datasets, files))
  }
}
