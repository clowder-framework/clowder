/**
 *
 */
package controllers

import api.{Permission, WithPermission}
import models.Previewer
import play.api.Play.current
import play.api.libs.json.Json
import play.api.mvc.Controller
import play.api.{Logger, Play}
import util.ResourceLister

import scala.Array.canBuildFrom
import scala.io.Source

/**
 * Previewers.
 *
 * @author Luigi Marini
 * @author Rob Kooper
 *
 */
object Previewers extends Controller with SecuredController {
  def list = SecuredAction(parse.anyContent, authorization=WithPermission(Permission.ShowFile)) { implicit request =>
    Ok(views.html.previewers(findPreviewers()))
  }

  def findPreviewers(): Array[Previewer] = {
    var result = Array[Previewer]()
    val previewers = ResourceLister.listFiles("public.javascripts.previewers", "package.json")
    for (previewer <- previewers) {
      Play.resourceAsStream(previewer) match {
        case Some(stream) => {
          val json = Json.parse(Source.fromInputStream(stream).mkString)
          result +:= Previewer((json \ "name").as[String],
            previewer.replace("public/", "").replace("/package.json", ""),
            (json \ "main").as[String],
            (json \ "contentType").as[List[String]],
            (json \ "supported_previews").asOpt[List[String]].getOrElse(List.empty[String]),
            (json \ "collection").asOpt[Boolean].getOrElse(false)
          )
        }
        case None => {
          Logger.warn("Thought I saw previewer " + previewer)
        }
      }
    }
    result
  }

  def findCollectionPreviewers(): Array[Previewer] = {
    var result = Array[Previewer]()
    val previewers = ResourceLister.listFiles("public.javascripts.previewers", "package.json")
    for (previewer <- previewers) {
      Play.resourceAsStream(previewer) match {
        case Some(stream) => {
          val json = Json.parse(Source.fromInputStream(stream).mkString)
          val preview = Previewer((json \ "name").as[String],
            previewer.replace("public/", "").replace("/package.json", ""),
            (json \ "main").as[String],
            (json \ "contentType").as[List[String]],
            (json \ "supported_previews").asOpt[List[String]].getOrElse(List.empty[String]),
            (json \ "collection").asOpt[Boolean].getOrElse(false)
          )
          if (preview.collection) result +:= preview
        }
        case None => {
          Logger.warn("Thought I saw previewer " + previewer)
        }
      }
    }
    result
  }

  def findDatasetPreviewers(): Array[Previewer] = {
    var result = Array[Previewer]()
    val previewers = ResourceLister.listFiles("public.javascripts.previewers", "package.json")
    for (previewer <- previewers) {
      Play.resourceAsStream(previewer) match {
        case Some(stream) => {
          val json = Json.parse(Source.fromInputStream(stream).mkString)
          val preview = Previewer((json \ "name").as[String],
            previewer.replace("public/", "").replace("/package.json", ""),
            (json \ "main").as[String],
            (json \ "contentType").as[List[String]],
            (json \ "supported_previews").asOpt[List[String]].getOrElse(List.empty[String]),
            (json \ "dataset").asOpt[Boolean].getOrElse(false)
          )
          if (preview.dataset) result +:= preview
        }
        case None => {
          Logger.warn("Thought I saw previewer " + previewer)
        }
      }
    }
    result
  }

}
