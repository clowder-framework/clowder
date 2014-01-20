package controllers

import play.api.mvc.Controller
import securesocial.core.SecureSocial
import play.api.Logger
import play.api.libs.json.Json._
import models.Dataset
import play.api.mvc.Action
import models.File
import models.FileDAO
import models.SectionDAO
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

  def search(tag: String) = SecuredAction(parse.anyContent, authorization = WithPermission(Permission.SearchDatasets)) { implicit request =>
    // Clean up leading, trailing and multiple contiguous white spaces.
    val tagCleaned = tag.trim().replaceAll("\\s+", " ")

    val datasets = Dataset.findByTag(tagCleaned)
    val files    = FileDAO.findByTag(tagCleaned)
    val sections = SectionDAO.findByTag(tagCleaned)
    //    Logger.debug("Search by tag " + tag + " returned " + datasets.length)
    Ok(views.html.searchByTag(tag, datasets, files, sections))
  }
}
