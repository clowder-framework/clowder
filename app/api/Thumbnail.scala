package api

import java.io.FileInputStream
import javax.inject.{Inject, Singleton}

import play.api.Logger
import play.api.libs.json.Json._
import play.api.mvc.Controller
import services.ThumbnailService


@Singleton
class Thumbnail @Inject() (thumbnails: ThumbnailService) extends Controller with ApiController {

  /**
   * Upload a file thumbnail.
   */  
  def uploadThumbnail() = PermissionAction(Permission.CreatePreview)(parse.multipartFormData) { request =>
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

}