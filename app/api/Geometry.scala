package api

import play.api.mvc.Controller
import play.api.mvc.Action
import models.GeometryDAO
import java.io.FileInputStream
import play.api.libs.json.Json._
import play.api.Logger

object Geometry extends Controller {

    /**
   * Upload a 3D binary geometry file.
   */  
  def uploadGeometry() = 
    Authenticated {
    Action(parse.multipartFormData) { implicit request =>
      request.body.file("File").map { f =>        
        Logger.info("Uploading binary geometry file " + f.filename)
        // store file
        val id = GeometryDAO.save(new FileInputStream(f.ref.file), f.filename, f.contentType)
        Ok(toJson(Map("id"->id)))   
      }.getOrElse {
         BadRequest(toJson("File not attached."))
      }
    }
  }
}