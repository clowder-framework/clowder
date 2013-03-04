/**
 *
 */
package controllers
import play.api.mvc.Controller
import securesocial.core.SecureSocial
import models.Previewer
import play.api.Play
import play.api.Logger
import play.api.mvc.Action
import models.Previewer
import play.api.libs.json.Json
import scala.io.Source
import play.api.Play.current

/**
 * Previewers.
 * 
 * @author Luigi Marini
 *
 */
object Previewers extends Controller with SecureSocial {
  
  val prefix = "javascripts/previewers/"
  
	def list = Action {
	  Ok(views.html.previewers(searchFileSystem))
	}
  
  def searchFileSystem(): Array[Previewer] = {
    for (
	      dir <- Play.getFile("public/" + prefix).listFiles();
	      file <- dir.listFiles()
	      if file.getName() == "package.json"
	  ) yield {
	      val json = Json.parse(Source.fromFile(file).mkString)
	      Previewer((json \ "name").as[String], 
	          prefix + dir.getName(), 
	          (json \ "main").as[String],
	          (json \ "contentType").as[List[String]]
	      )
	  }
  }
}