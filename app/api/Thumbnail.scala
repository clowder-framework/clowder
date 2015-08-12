package api


import java.io.FileInputStream
import javax.inject.{Inject, Singleton}

import com.wordnik.swagger.annotations.{Api, ApiOperation}
import models.{Thumbnail, UUID}
import play.api.Logger
import play.api.libs.json.JsValue
import play.api.libs.json.Json._
import play.api.mvc.Controller
import services.ThumbnailService


@Singleton
@Api(value = "/thumbnails", listingPath = "/api-docs.json/thumbnails", description = "A thumbnail is the raw bytes plus metadata.")
class Thumbnails @Inject() (thumbnails: ThumbnailService) extends Controller with ApiController {

  /**
   * List all files.
   */
  @ApiOperation(value = "List all thumbnail files", notes = "Returns list of thumbnail files and descriptions.", responseClass = "None", httpMethod = "GET")
  def list = PrivateServerAction { implicit request =>
    val list = for (t <- thumbnails.listThumbnails()) yield jsonThumbnail(t)
    Ok(toJson(list))
  }

  @ApiOperation(value = "Delete thumbnail",
    notes = "Remove thumbnail file from system).",
    responseClass = "None", httpMethod = "POST")
  def removeThumbnail(id: UUID) = PrivateServerAction { implicit request =>
    thumbnails.get(id) match {
      case Some(thumbnail) => {
        Logger.debug("Deleting file: " + thumbnail.filename)
        thumbnails.remove(id)
        Ok(toJson(Map("status"->"success")))
      }
      case None => {
        Logger.debug("Couldn't find thumbnail")
        Ok(toJson(Map("status" -> "success")))
      }
    }
  }

  /**
   * Upload a file thumbnail.
   */  
  def uploadThumbnail() = PermissionAction(Permission.CreatePreview)(parse.multipartFormData) { implicit request =>
    request.body.file("File").map { f =>
      f.ref.file.length() match{
        case 0L => {
          BadRequest(toJson("File is empty."))
        }
        case _ => {
          Logger.info("Uploading thumbnail " + f.filename)
        // store file
        val id = thumbnails.save(new FileInputStream(f.ref.file), f.filename, f.contentType)
        Ok(toJson(Map("id"->id)))
        }
      }
    }.getOrElse {
       BadRequest(toJson("File not attached."))
    }
  }

  def jsonThumbnail(thumbnail: Thumbnail): JsValue = {
    toJson(Map("id" -> thumbnail.id.toString(), "chunksize" -> thumbnail.chunkSize.toString(),  "filename" -> thumbnail.filename.getOrElse(""),
      "content-type" -> thumbnail.contentType, "date-created" -> thumbnail.uploadDate.toString(), "size" -> thumbnail.length.toString()))

  }
}