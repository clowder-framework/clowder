<<<<<<< Upstream, based on play-2.2
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
import models.TileDAO
import com.mongodb.WriteConcern
import models.ThreeDAnnotation
import play.api.libs.json.JsValue
import controllers.SecuredController
import play.api.libs.concurrent.Execution.Implicits._
import java.io.BufferedReader
import java.io.FileReader

/**
 * Files and datasets previews.
 * 
 * @author Luigi Marini
 *
 */
object Previews extends ApiController {

  def downloadPreview(id:String, datasetid:String) =
    SecuredAction(parse.anyContent, authorization=WithPermission(Permission.ShowFile)) { request =>
      Redirect(routes.Previews.download(id))    
  }
  
  /**
   * Download preview bytes.
   */
  def download(id:String) =
    SecuredAction(parse.anyContent, authorization=WithPermission(Permission.ShowFile)) { request =>
	    PreviewDAO.getBlob(id) match {
	   
	      case Some((inputStream, filename, contentType, contentLength)) => {
    	    request.headers.get(RANGE) match {
	          case Some(value) => {
	            val range: (Long,Long) = value.substring("bytes=".length).split("-") match {
	              case x if x.length == 1 => (x.head.toLong, contentLength - 1)
	              case x => (x(0).toLong,x(1).toLong)
	            }
	            range match { case (start,end) =>
	             
	              inputStream.skip(start)
	              import play.api.mvc.{SimpleResult, ResponseHeader}
	              SimpleResult(
	                header = ResponseHeader(PARTIAL_CONTENT,
	                  Map(
	                    CONNECTION -> "keep-alive",
	                    ACCEPT_RANGES -> "bytes",
	                    CONTENT_RANGE -> "bytes %d-%d/%d".format(start,end,contentLength),
	                    CONTENT_LENGTH -> (end - start + 1).toString,
	                    CONTENT_TYPE -> contentType
	                  )
	                ),
	                body = Enumerator.fromStream(inputStream)
	              )
	            }
	          }
	          case None => {
	            Ok.stream(Enumerator.fromStream(inputStream))
	            	.withHeaders(CONTENT_TYPE -> contentType)
	            	.withHeaders(CONTENT_LENGTH -> contentLength.toString)
	            	.withHeaders(CONTENT_DISPOSITION -> ("attachment; filename=" + filename))
      
	          }
	        }
	      }
	      case None => Logger.error("No preview find " + id); InternalServerError("No preview found")
	    }
    }

  
  /**
   * Upload a preview.
   */  
  def upload(iipKey: String="") = 
    SecuredAction(parse.multipartFormData, authorization=WithPermission(Permission.CreateFiles)) { implicit request =>
      request.body.file("File").map { f =>        
        Logger.debug("Uploading file " + f.filename)
        // store file
        val id = PreviewDAO.save(new FileInputStream(f.ref.file), f.filename, f.contentType)
        // for IIP server references, store the IIP URL, key and filename on the IIP server for possible later deletion of the previewed file
	        if(f.filename.endsWith(".imageurl")){
	          val iipRefReader = new BufferedReader(new FileReader(f.ref.file));
	          
	          val serverLine = iipRefReader.readLine();
	          var urlEnd = serverLine.indexOf("/",serverLine.indexOf("://")+3)
	          if(urlEnd == -1){
	            urlEnd = serverLine.length()
	          }
	          val iipURL = serverLine.substring(8,urlEnd)
	          
	          val imageLine = iipRefReader.readLine();
	          val iipImage = imageLine.substring(imageLine.lastIndexOf("/")+1)
	          
	          iipRefReader.close()
	          
	          PreviewDAO.setIIPReferences(id, iipURL, iipImage, iipKey)  
	        }
        Ok(toJson(Map("id"->id)))   
      }.getOrElse {
         BadRequest(toJson("File not attached."))
      }
    }
  
  /**
   * Upload preview metadata.
   * 
   */
  def uploadMetadata(id: String) = 
    SecuredAction(authorization=WithPermission(Permission.CreateFiles)) { request =>
      Logger.debug(request.body.toString)
      request.body match {
        case JsObject(fields) => {
          Logger.debug(fields.toString)
	      PreviewDAO.findOneById(new ObjectId(id)) match {
	        case Some(preview) =>
	            val metadata = fields.toMap.flatMap(tuple => MongoDBObject(tuple._1 -> tuple._2.as[String]))
	            val result = PreviewDAO.dao.collection.update(MongoDBObject("_id" -> new ObjectId(id)), 
	                $set("metadata" -> metadata, "section_id"->new ObjectId(metadata("section_id").asInstanceOf[String])), false, false, WriteConcern.SAFE)
	            Logger.debug("Updating previews.files " + id + " with " + metadata)
	            Ok(toJson(Map("status"->"success")))
	        case None => BadRequest(toJson("Preview not found"))
	      }
        }
        case _ => Logger.error("Expected a JSObject"); BadRequest(toJson("Expected a JSObject"))
      }
    }
  
  /**
   * Get preview metadata.
   * 
   */
  def getMetadata(id: String) =
    SecuredAction(parse.anyContent, authorization=WithPermission(Permission.ShowFile)) { request =>
      PreviewDAO.findOneById(new ObjectId(id)) match {
        case Some(preview) => Ok(toJson(Map("id"->preview.id.toString)))
        case None => Logger.error("Preview metadata not found " + id); InternalServerError
      }
    }
  
  
   /**
   * Add pyramid tile to preview.
   */
  def attachTile(preview_id: String, tile_id: String, level: String) = 
	    SecuredAction(authorization=WithPermission(Permission.CreateFiles)) { request =>
	      request.body match {
	        case JsObject(fields) => {
	          // TODO create a service instead of calling salat directly
	          PreviewDAO.findOneById(new ObjectId(preview_id)) match { 
	            case Some(preview) => {
		              TileDAO.findOneById(new ObjectId(tile_id)) match {
		                case Some(tile) =>
		                    val metadata = fields.toMap.flatMap(tuple => MongoDBObject(tuple._1 -> tuple._2.as[String]))
		                    TileDAO.dao.collection.update(MongoDBObject("_id" -> new ObjectId(tile_id)), 
		                        $set("metadata"-> metadata, "preview_id" -> new ObjectId(preview_id), "level"->level), false, false, WriteConcern.SAFE)
	//	                    Logger.debug("Updating tiles.files " + tile_id + " with " + metadata)
		                    Ok(toJson(Map("status"->"success")))
		                case None => BadRequest(toJson("Tile not found"))
		              }
	            }
		        case None => BadRequest(toJson("Preview not found " + preview_id))
		      }
	        }
	        case _ => Ok("received something else: " + request.body + '\n')
	    }
    }

  
  
  /**
   * Find tile for given preview, level and filename (row and column).
   */
  def getTile(dzi_id_dir: String, level: String, filename: String) =
    SecuredAction(parse.anyContent, authorization=WithPermission(Permission.ShowFile)) { request => 
      val dzi_id = dzi_id_dir.replaceAll("_files", "")
      TileDAO.findTile(new ObjectId(dzi_id), filename, level) match {
        case Some(tile) => {
          
          TileDAO.getBlob(tile.id.toString()) match {
            
            case Some((inputStream, filename, contentType, contentLength)) => {
    	    request.headers.get(RANGE) match {
	          case Some(value) => {
	            val range: (Long,Long) = value.substring("bytes=".length).split("-") match {
	              case x if x.length == 1 => (x.head.toLong, contentLength - 1)
	              case x => (x(0).toLong,x(1).toLong)
	            }
	            range match { case (start,end) =>
	             
	              inputStream.skip(start)
	              import play.api.mvc.{SimpleResult, ResponseHeader}
	              SimpleResult(
	                header = ResponseHeader(PARTIAL_CONTENT,
	                  Map(
	                    CONNECTION -> "keep-alive",
	                    ACCEPT_RANGES -> "bytes",
	                    CONTENT_RANGE -> "bytes %d-%d/%d".format(start,end,contentLength),
	                    CONTENT_LENGTH -> (end - start + 1).toString,
	                    CONTENT_TYPE -> contentType
	                  )
	                ),
	                body = Enumerator.fromStream(inputStream)
	              )
	            }
	          }
	          case None => {
	            Ok.stream(Enumerator.fromStream(inputStream))
	            	.withHeaders(CONTENT_TYPE -> contentType)
	            	.withHeaders(CONTENT_LENGTH -> contentLength.toString)
	            	.withHeaders(CONTENT_DISPOSITION -> ("attachment; filename=" + filename))
      
	          }
	        }
	      }
	      case None => Logger.error("No tile found: " + tile.id.toString()); InternalServerError("No tile found")
            
          }
          
        }         
        case None => Logger.error("Tile not found"); InternalServerError
      }
    }
  
    /**
   * Add annotation to 3D model preview.
   */
  def attachAnnotation(preview_id: String) = 
    SecuredAction(authorization=WithPermission(Permission.CreateFiles)) { request =>       	  
	      val x_coord = request.body.\("x_coord").asOpt[String].getOrElse("0.0")
	      val y_coord = request.body.\("y_coord").asOpt[String].getOrElse("0.0")
	      val z_coord = request.body.\("z_coord").asOpt[String].getOrElse("0.0")
	      val description = request.body.\("description").asOpt[String].getOrElse("")
      
          // TODO create a service instead of calling salat directly
          PreviewDAO.findOneById(new ObjectId(preview_id)) match { 
            case Some(preview) => {
            	val annotation = ThreeDAnnotation(x_coord, y_coord, z_coord, description)
            	PreviewDAO.annotation(preview_id, annotation)             
            	Ok(toJson(Map("status"->"success")))
            }
	        case None => BadRequest(toJson("Preview not found " + preview_id))
	      }
    }
    
  def editAnnotation(preview_id: String) =
    SecuredAction(authorization=WithPermission(Permission.CreateFiles)) { request =>
      Logger.debug("thereq: " + request.body.toString) 
      	  val x_coord = request.body.\("x_coord").asOpt[String].getOrElse("0.0")
	      val y_coord = request.body.\("y_coord").asOpt[String].getOrElse("0.0")
	      val z_coord = request.body.\("z_coord").asOpt[String].getOrElse("0.0")
	      val description = request.body.\("description").asOpt[String].getOrElse("")
	      
	      // TODO create a service instead of calling salat directly
          PreviewDAO.findOneById(new ObjectId(preview_id)) match { 
            case Some(preview) => {
            	PreviewDAO.findAnnotation(preview_id, x_coord, y_coord, z_coord) match{
            	  case Some(annotation) => {
            		PreviewDAO.updateAnnotation(preview_id, annotation.id.toString(), description)
            	    Ok(toJson(Map("status"->"success")))
            	  }
            	  case None => Ok(toJson(Map("status"->"success"))) //What the user sees locally must not change if an annotation is deleted after the user loads the dataset
            			  											//but before attempting to modify the selected annotation's description.
            	    //BadRequest(toJson("Annotation for preview " + preview_id + " not found: " + x_coord + "," + y_coord + "," + z_coord))
            	}
            }
	        case None => BadRequest(toJson("Preview not found " + preview_id))
	      }
  }
  
   def listAnnotations(preview_id: String) =
    SecuredAction(parse.anyContent, authorization=WithPermission(Permission.ShowFile)){ request => 
	      // TODO create a service instead of calling salat directly
          PreviewDAO.findOneById(new ObjectId(preview_id)) match { 
            case Some(preview) => {
            	val annotationsOfPreview = PreviewDAO.listAnnotations(preview_id)
            	val list = for (annotation <- annotationsOfPreview) yield jsonAnnotation(annotation)           	
            	Logger.debug("thelist: " + toJson(list))
	      	  	Ok(toJson(list))
            }
	        case None => BadRequest(toJson("Preview not found " + preview_id))
	      }
  }
   
    def jsonAnnotation(annotation: ThreeDAnnotation): JsValue = {
    		toJson(Map("x_coord"->annotation.x_coord.toString, "y_coord"->annotation.y_coord.toString, "z_coord"->annotation.z_coord.toString,"description"->annotation.description))
  }
  
}
=======
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
import models.TileDAO
import com.mongodb.WriteConcern
import models.ThreeDAnnotation
import play.api.libs.json.JsValue
import controllers.SecuredController
import play.api.libs.concurrent.Execution.Implicits._

/**
 * Files and datasets previews.
 * 
 * @author Luigi Marini
 *
 */
object Previews extends ApiController {

  def downloadPreview(id:String, datasetid:String) =
    SecuredAction(parse.anyContent, authorization=WithPermission(Permission.ShowFile)) { request =>
      Redirect(routes.Previews.download(id))    
  }
  
  /**
   * Download preview bytes.
   */
  def download(id:String) =
    SecuredAction(parse.anyContent, authorization=WithPermission(Permission.ShowFile)) { request =>
	    PreviewDAO.getBlob(id) match {
	   
	      case Some((inputStream, filename, contentType, contentLength)) => {
    	    request.headers.get(RANGE) match {
	          case Some(value) => {
	            val range: (Long,Long) = value.substring("bytes=".length).split("-") match {
	              case x if x.length == 1 => (x.head.toLong, contentLength - 1)
	              case x => (x(0).toLong,x(1).toLong)
	            }
	            range match { case (start,end) =>
	             
	              inputStream.skip(start)
	              import play.api.mvc.{SimpleResult, ResponseHeader}
	              SimpleResult(
	                header = ResponseHeader(PARTIAL_CONTENT,
	                  Map(
	                    CONNECTION -> "keep-alive",
	                    ACCEPT_RANGES -> "bytes",
	                    CONTENT_RANGE -> "bytes %d-%d/%d".format(start,end,contentLength),
	                    CONTENT_LENGTH -> (end - start + 1).toString,
	                    CONTENT_TYPE -> contentType
	                  )
	                ),
	                body = Enumerator.fromStream(inputStream)
	              )
	            }
	          }
	          case None => {
	            Ok.stream(Enumerator.fromStream(inputStream))
	            	.withHeaders(CONTENT_TYPE -> contentType)
	            	.withHeaders(CONTENT_LENGTH -> contentLength.toString)
	            	.withHeaders(CONTENT_DISPOSITION -> ("attachment; filename=" + filename))
      
	          }
	        }
	      }
	      case None => Logger.error("No preview find " + id); InternalServerError("No preview found")
	    }
    }

  
  /**
   * Upload a preview.
   */  
  def upload() = 
    SecuredAction(parse.multipartFormData, authorization=WithPermission(Permission.CreateFiles)) { implicit request =>
      request.body.file("File").map { f =>        
        Logger.debug("Uploading file " + f.filename)
        // store file
        val id = PreviewDAO.save(new FileInputStream(f.ref.file), f.filename, f.contentType)
        Ok(toJson(Map("id"->id)))   
      }.getOrElse {
         BadRequest(toJson("File not attached."))
      }
    }
  
  /**
   * Upload preview metadata.
   * 
   */
  def uploadMetadata(id: String) = 
    SecuredAction(authorization=WithPermission(Permission.CreateFiles)) { request =>
      Logger.debug(request.body.toString)
      request.body match {
        case JsObject(fields) => {
          Logger.debug(fields.toString)
	      PreviewDAO.findOneById(new ObjectId(id)) match {
	        case Some(preview) =>
	            val metadata = fields.toMap.flatMap(tuple => MongoDBObject(tuple._1 -> tuple._2.as[String]))
	            val result = PreviewDAO.dao.collection.update(MongoDBObject("_id" -> new ObjectId(id)), 
	                $set("metadata" -> metadata, "section_id"->new ObjectId(metadata("section_id").asInstanceOf[String])), false, false, WriteConcern.SAFE)
	            Logger.debug("Updating previews.files " + id + " with " + metadata)
	            Ok(toJson(Map("status"->"success")))
	        case None => BadRequest(toJson("Preview not found"))
	      }
        }
        case _ => Logger.error("Expected a JSObject"); BadRequest(toJson("Expected a JSObject"))
      }
    }
  
  /**
   * Get preview metadata.
   * 
   */
  def getMetadata(id: String) =
    SecuredAction(parse.anyContent, authorization=WithPermission(Permission.ShowFile)) { request =>
      PreviewDAO.findOneById(new ObjectId(id)) match {
        case Some(preview) => Ok(toJson(Map("id"->preview.id.toString)))
        case None => Logger.error("Preview metadata not found " + id); InternalServerError
      }
    }
  
  
   /**
   * Add pyramid tile to preview.
   */
  def attachTile(preview_id: String, tile_id: String, level: String) = 
	    SecuredAction(authorization=WithPermission(Permission.CreateFiles)) { request =>
	      request.body match {
	        case JsObject(fields) => {
	          // TODO create a service instead of calling salat directly
	          PreviewDAO.findOneById(new ObjectId(preview_id)) match { 
	            case Some(preview) => {
		              TileDAO.findOneById(new ObjectId(tile_id)) match {
		                case Some(tile) =>
		                    val metadata = fields.toMap.flatMap(tuple => MongoDBObject(tuple._1 -> tuple._2.as[String]))
		                    TileDAO.dao.collection.update(MongoDBObject("_id" -> new ObjectId(tile_id)), 
		                        $set("metadata"-> metadata, "preview_id" -> new ObjectId(preview_id), "level"->level), false, false, WriteConcern.SAFE)
	//	                    Logger.debug("Updating tiles.files " + tile_id + " with " + metadata)
		                    Ok(toJson(Map("status"->"success")))
		                case None => BadRequest(toJson("Tile not found"))
		              }
	            }
		        case None => BadRequest(toJson("Preview not found " + preview_id))
		      }
	        }
	        case _ => Ok("received something else: " + request.body + '\n')
	    }
    }

  
  
  /**
   * Find tile for given preview, level and filename (row and column).
   */
  def getTile(dzi_id_dir: String, level: String, filename: String) =
    SecuredAction(parse.anyContent, authorization=WithPermission(Permission.ShowFile)) { request => 
      val dzi_id = dzi_id_dir.replaceAll("_files", "")
      TileDAO.findTile(new ObjectId(dzi_id), filename, level) match {
        case Some(tile) => {
          
          TileDAO.getBlob(tile.id.toString()) match {
            
            case Some((inputStream, filename, contentType, contentLength)) => {
    	    request.headers.get(RANGE) match {
	          case Some(value) => {
	            val range: (Long,Long) = value.substring("bytes=".length).split("-") match {
	              case x if x.length == 1 => (x.head.toLong, contentLength - 1)
	              case x => (x(0).toLong,x(1).toLong)
	            }
	            range match { case (start,end) =>
	             
	              inputStream.skip(start)
	              import play.api.mvc.{SimpleResult, ResponseHeader}
	              SimpleResult(
	                header = ResponseHeader(PARTIAL_CONTENT,
	                  Map(
	                    CONNECTION -> "keep-alive",
	                    ACCEPT_RANGES -> "bytes",
	                    CONTENT_RANGE -> "bytes %d-%d/%d".format(start,end,contentLength),
	                    CONTENT_LENGTH -> (end - start + 1).toString,
	                    CONTENT_TYPE -> contentType
	                  )
	                ),
	                body = Enumerator.fromStream(inputStream)
	              )
	            }
	          }
	          case None => {
	            Ok.stream(Enumerator.fromStream(inputStream))
	            	.withHeaders(CONTENT_TYPE -> contentType)
	            	.withHeaders(CONTENT_LENGTH -> contentLength.toString)
	            	.withHeaders(CONTENT_DISPOSITION -> ("attachment; filename=" + filename))
      
	          }
	        }
	      }
	      case None => Logger.error("No tile found: " + tile.id.toString()); InternalServerError("No tile found")
            
          }
          
        }         
        case None => Logger.error("Tile not found"); InternalServerError
      }
    }
  
    /**
   * Add annotation to 3D model preview.
   */
  def attachAnnotation(preview_id: String) = 
    SecuredAction(authorization=WithPermission(Permission.CreateFiles)) { request =>       	  
	      val x_coord = request.body.\("x_coord").asOpt[String].getOrElse("0.0")
	      val y_coord = request.body.\("y_coord").asOpt[String].getOrElse("0.0")
	      val z_coord = request.body.\("z_coord").asOpt[String].getOrElse("0.0")
	      val description = request.body.\("description").asOpt[String].getOrElse("")
      
          // TODO create a service instead of calling salat directly
          PreviewDAO.findOneById(new ObjectId(preview_id)) match { 
            case Some(preview) => {
            	val annotation = ThreeDAnnotation(x_coord, y_coord, z_coord, description)
            	PreviewDAO.annotation(preview_id, annotation)             
            	Ok(toJson(Map("status"->"success")))
            }
	        case None => BadRequest(toJson("Preview not found " + preview_id))
	      }
    }
    
  def editAnnotation(preview_id: String) =
    SecuredAction(authorization=WithPermission(Permission.CreateFiles)) { request =>
      Logger.debug("thereq: " + request.body.toString) 
      	  val x_coord = request.body.\("x_coord").asOpt[String].getOrElse("0.0")
	      val y_coord = request.body.\("y_coord").asOpt[String].getOrElse("0.0")
	      val z_coord = request.body.\("z_coord").asOpt[String].getOrElse("0.0")
	      val description = request.body.\("description").asOpt[String].getOrElse("")
	      
	      // TODO create a service instead of calling salat directly
          PreviewDAO.findOneById(new ObjectId(preview_id)) match { 
            case Some(preview) => {
            	PreviewDAO.findAnnotation(preview_id, x_coord, y_coord, z_coord) match{
            	  case Some(annotation) => {
            		PreviewDAO.updateAnnotation(preview_id, annotation.id.toString(), description)
            	    Ok(toJson(Map("status"->"success")))
            	  }
            	  case None => Ok(toJson(Map("status"->"success"))) //What the user sees locally must not change if an annotation is deleted after the user loads the dataset
            			  											//but before attempting to modify the selected annotation's description.
            	    //BadRequest(toJson("Annotation for preview " + preview_id + " not found: " + x_coord + "," + y_coord + "," + z_coord))
            	}
            }
	        case None => BadRequest(toJson("Preview not found " + preview_id))
	      }
  }
  
   def listAnnotations(preview_id: String) =
    SecuredAction(parse.anyContent, authorization=WithPermission(Permission.ShowFile)){ request => 
	      // TODO create a service instead of calling salat directly
          PreviewDAO.findOneById(new ObjectId(preview_id)) match { 
            case Some(preview) => {
            	val annotationsOfPreview = PreviewDAO.listAnnotations(preview_id)
            	val list = for (annotation <- annotationsOfPreview) yield jsonAnnotation(annotation)           	
            	Logger.debug("thelist: " + toJson(list))
	      	  	Ok(toJson(list))
            }
	        case None => BadRequest(toJson("Preview not found " + preview_id))
	      }
  }
   
    def jsonAnnotation(annotation: ThreeDAnnotation): JsValue = {
    		toJson(Map("x_coord"->annotation.x_coord.toString, "y_coord"->annotation.y_coord.toString, "z_coord"->annotation.z_coord.toString,"description"->annotation.description))
  }
  
}
>>>>>>> 8d0afe8 Upgraded to play 2.2. Compile errors are all fixed plus a few runtime errors. Current runtime error is:
