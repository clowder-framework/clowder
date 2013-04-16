package api

import play.api.mvc.Controller
import play.api.mvc.Action
import models.TileDAO
import java.io.FileInputStream
import play.api.libs.json.Json._
import play.api.Logger

object ZoomIt extends Controller {

     /**
   * Upload a pyramid tile.
   */  
  def uploadTile() = 
//    Authenticated {
    Action(parse.multipartFormData) { implicit request =>
      request.body.file("File").map { f =>        
        Logger.info("Uploading pyramid tile " + f.filename.substring(0, f.filename.lastIndexOf("_")) + f.filename.substring(f.filename.lastIndexOf(".")))
        // store file
        val id = TileDAO.save(new FileInputStream(f.ref.file), f.filename.substring(0, f.filename.lastIndexOf("_")) + f.filename.substring(f.filename.lastIndexOf(".")), f.contentType)
        Ok(toJson(Map("id"->id)))   
      }.getOrElse {
         BadRequest(toJson("File not attached."))
      }
    }
//  }
  
  
  
}