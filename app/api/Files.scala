/**
 *
 */
package api
import play.api.mvc.Controller
import play.api.mvc.Action
import play.api.libs.json.Json._
import services.Services
import play.api.Logger
import models.File
import play.api.libs.json.JsValue

/**
 * Json API for files.
 * 
 * @author Luigi Marini
 *
 */
object Files extends Controller {
  
  def get(id: String) = Action {
    Logger.info("GET file with id " + id)    
    Services.files.getFile(id) match {
      case Some(file) => Ok(jsonFile(file))
      case None => {Logger.error("Error getting file" + id); InternalServerError}
    }
  }
  
  def list = Action {
    val list = for (f <- Services.files.listFiles()) yield jsonFile(f)
    Ok(toJson(list))
  }
  
  def jsonFile(file: File): JsValue = {
    toJson(Map("id"->file.id.toString, "filename"->file.filename, "content-type"->file.contentType))
  }
}