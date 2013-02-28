/**
 *
 */
package api

import play.api.mvc.Controller
import play.api.mvc.Action
import play.api.Logger
import models.PreviewDAO
import java.io.FileInputStream
import play.api.libs.json.Json._
import play.api.libs.iteratee.Enumerator

/**
 * Files and datasets previews.
 * 
 * @author Luigi Marini
 *
 */
object Previews extends Controller {

  /**
   * Download preview bytes.
   */
  def download(id:String) = Authenticated {
    Action {
	    PreviewDAO.getBlob(id) match {
	      case Some((inputStream, filename)) => {
	    	Ok.stream(Enumerator.fromStream(inputStream))
	    	  .withHeaders(CONTENT_DISPOSITION -> ("attachment; filename=" + filename))
	      }
	      case None => {
	        Logger.error("Error getting file" + id)
	        NotFound
	      }
	    }
    }
  }
  
  /**
   * Upload a preview.
   */  
  def upload() = Authenticated {
    Action(parse.multipartFormData) { implicit request =>
      request.body.file("File").map { f =>        
        Logger.info("Uploading file " + f.filename)
        // store file
        val id = PreviewDAO.save(new FileInputStream(f.ref.file), f.filename, f.contentType)
        Ok(toJson(Map("id"->id)))   
      }.getOrElse {
         BadRequest(toJson("File not attached."))
      }
    }
  }
  
}