package controllers

import api.Permission
import models.Previewer
import play.api.Play.current
import play.api.libs.json.Json
import play.api.mvc.Controller
import play.api.{Logger, Play}
import util.ResourceLister

import scala.collection.mutable.ListBuffer
import scala.io.Source

/**
 * Previewers are a way to visualize information about the file, dataset or collection in the web frontend.
 */
object Previewers extends Controller with SecuredController {

  def list = PermissionAction(Permission.ViewFile) { implicit request =>
    Ok(views.html.previewers(findPreviewers()))
  }

  private def findPreviewers(): List[Previewer] = {
    var result = ListBuffer[Previewer]()
    val previewers = ResourceLister.listFiles("public.javascripts.previewers", "package.json")
    for (previewer <- previewers) {
      Play.resourceAsStream(previewer) match {
        case Some(stream) => {
          val json = Json.parse(Source.fromInputStream(stream).mkString)
          result += Previewer((json \ "name").as[String],
            previewer.replace("public/", "").replace("/package.json", ""),
            (json \ "main").as[String],
            (json \ "contentType").as[List[String]],
            (json \ "supported_previews").asOpt[List[String]].getOrElse(List.empty[String]),
            (json \ "file").asOpt[Boolean].getOrElse(false),
            (json \ "preview").asOpt[Boolean].getOrElse(false),
            (json \ "dataset").asOpt[Boolean].getOrElse(false),
            (json \ "collection").asOpt[Boolean].getOrElse(false)
          )
        }
        case None => {
          Logger.warn("Could not load previewer from disk " + previewer)
        }
      }
    }
    result.toList
  }

  def findFilePreviewers(): List[Previewer] = findPreviewers().filter(p => p.file || p.preview)

  def findCollectionPreviewers(): List[Previewer] = findPreviewers().filter(p => p.collection)

  def findDatasetPreviewers(): List[Previewer] = findPreviewers().filter(p => p.dataset)
}
