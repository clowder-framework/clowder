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
import services.Services
import scala.util.parsing.json.JSONArray

/**
 * Tagging.
 * 
 * @author Luigi Marini
 */
object Tags extends SecuredController {

  def search(tag: String) = SecuredAction(parse.multipartFormData, authorization=WithPermission(Permission.SearchDatasets)) { implicit request =>
    val datasets = Dataset.findByTag(tag)
    val files = FileDAO.findByTag(tag)
//    Logger.debug("Search by tag " + tag + " returned " + datasets.length)
    Ok(views.html.searchByTag(tag, datasets, files))
  }
}
