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
import play.api.libs.json.Json._
import com.mongodb.casbah.Imports._
import org.bson.types.ObjectId
import play.api.libs.json.Json
import play.api.libs.json.JsObject

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
	      case Some((inputStream, filename, contentType)) => {
	    	Ok.stream(Enumerator.fromStream(inputStream))
	    	  .withHeaders(CONTENT_TYPE -> contentType)
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
  
  /**
   * Upload preview metadata.
   * 
   */
  def uploadMetadata(id: String) = Authenticated {
    Action(parse.json) { request =>
      request.body match {
        case JsObject(fields) => {
	      PreviewDAO.findOneById(new ObjectId(id)) match {
	        case Some(preview) =>
	            val metadata = fields.toMap.flatMap(tuple => MongoDBObject(tuple._1 -> tuple._2.as[String]))
	            val result = PreviewDAO.dao.collection.update(MongoDBObject("_id" -> new ObjectId(id)), 
	                $set("metadata" -> metadata, 
	                    "section_id"->new ObjectId(metadata("section_id").asInstanceOf[String])), false, false, WriteConcern.Safe)
	            Logger.debug("Updating previews.files " + id + " with " + metadata)
	            Ok(toJson(Map("status"->"success")))
	        case None => BadRequest(toJson("Preview not found"))
	      }
        }
      }
    }
  }
  
  /**
   * Get preview metadata.
   * 
   */
  def getMetadata(id: String) = Authenticated {
    Action { request =>
      PreviewDAO.findOneByID(new ObjectId(id)) match {
        case Some(preview) => Ok(toJson(Map("id"->preview.id.toString)))
        case None => Logger.error("Preview metadata not found " + id); InternalServerError
      }
    }
  }
  
}