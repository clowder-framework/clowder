package api

import play.api.mvc.Controller
import play.api.mvc.Action
import java.io.FileInputStream
import play.api.libs.json.Json._
import play.api.Logger

object Thumbnail extends Controller with ApiController {
  
   /**
   * Upload a file thumbnail.
   */  
  def uploadThumbnail() = SecuredAction(parse.multipartFormData, authorization=WithPermission(Permission.AddThumbnail)) { request => 
      request.body.file("File").map { f =>
        f.ref.file.length() match{
          case 0L => {
            BadRequest(toJson("File is empty."))
          }
          case _ => {
            Logger.info("Uploading thumbnail " + f.filename)
	        // store file
	        val id = models.Thumbnail.save(new FileInputStream(f.ref.file), f.filename, f.contentType)
	        Ok(toJson(Map("id"->id))) 
          }
        }  
      }.getOrElse {
         BadRequest(toJson("File not attached."))
      }
  }

}