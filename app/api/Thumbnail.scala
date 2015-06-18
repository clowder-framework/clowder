package api

import play.api.mvc.Controller
import java.io.FileInputStream
import play.api.libs.json.Json._
import play.api.Logger
import javax.inject.{Inject, Singleton}
import services.ThumbnailService


@Singleton
class Thumbnail @Inject() (thumbnails: ThumbnailService) extends Controller with ApiController {
  
   /**
   * Upload a file thumbnail.
   */  
  def uploadThumbnail() = SecuredAction(parse.multipartFormData, authorization=WithPermission(Permission.CreatePreview)) { request =>
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