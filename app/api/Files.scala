/**
 *
 */
package api

import java.io.FileInputStream
import java.util.Date

import org.bson.types.ObjectId

import com.mongodb.WriteConcern
import com.mongodb.casbah.Imports._

import controllers.SecuredController
import controllers.Previewers
import fileutils.FilesUtils
import models.Comment
import models.Dataset
import models.File
import models.FileDAO
import models.GeometryDAO
import models.Preview
import models.PreviewDAO
import models.ThreeDTextureDAO
import models.Extraction
import play.api.Logger
import play.api.Play.current
import play.api.libs.iteratee.Enumerator
import play.api.libs.json._
import play.api.libs.json.JsValue
import play.api.libs.json.Json._
import play.api.mvc.Action
import play.api.mvc.Controller
import services.ElasticsearchPlugin
import services.ExtractorMessage
import services.RabbitmqPlugin
import services.Services

/**
 * Json API for files.
 * 
 * @author Luigi Marini
 *
 */
object Files extends ApiController {
  
  def get(id: String) = SecuredAction(parse.anyContent, authorization=WithPermission(Permission.ShowFile)) { implicit request =>
	    Logger.info("GET file with id " + id)    
	    Services.files.getFile(id) match {
	      case Some(file) => Ok(jsonFile(file))
	      case None => {Logger.error("Error getting file" + id); InternalServerError}
	    }
  }
  
  /**
   * List all files.
   */
  def list = SecuredAction(parse.anyContent, authorization=WithPermission(Permission.ListFiles)) { request =>
      val list = for (f <- Services.files.listFiles()) yield jsonFile(f)
      Ok(toJson(list))
    }
  
  def downloadByDatasetAndFilename(dataset_id: String, filename: String, preview_id: String) = 
    SecuredAction(parse.anyContent, authorization=WithPermission(Permission.DownloadFiles)){ request =>
      Datasets.datasetFilesGetIdByDatasetAndFilename(dataset_id, filename) match{
        case Some(id) => { 
          Redirect(routes.Files.download(id)) 
        }
        case None => {
          InternalServerError
        }
      }
  
    }
  
  
  /**
   * Download file using http://en.wikipedia.org/wiki/Chunked_transfer_encoding
   */
  def download(id: String) = 
	    SecuredAction(parse.anyContent, authorization=WithPermission(Permission.DownloadFiles)) { request =>
//		  Action(parse.anyContent) { request =>
		    Services.files.get(id) match {
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
		      case None => {
		        Logger.error("Error getting file" + id)
		        NotFound
		      }
		    }
	    }
    
  /// /******Download query used by Versus**********/
  def downloadquery(id: String) = 
	    SecuredAction(parse.anyContent, authorization=WithPermission(Permission.DownloadFiles)) { request =>
//		  Action(parse.anyContent) { request =>
		    Services.queries.get(id) match {
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
		      case None => {
		        Logger.error("Error getting file" + id)
		        NotFound
		      }
		    }
	    }
  /**
   * Add metadata to file.
   */
  def addMetadata(id: String) =  
   SecuredAction(authorization=WithPermission(Permission.DownloadFiles)) { request =>
      Logger.debug("Adding metadata to file " + id)
     val doc = com.mongodb.util.JSON.parse(Json.stringify(request.body)).asInstanceOf[DBObject]
     FileDAO.dao.collection.findOneByID(new ObjectId(id)) match {
	      case Some(x) => {
	    	  x.getAs[DBObject]("metadata") match {
	    	  case Some(map) => {
	    		  val union = map.asInstanceOf[DBObject] ++ doc
	    		  FileDAO.dao.collection.update(MongoDBObject("_id" -> new ObjectId(id)), $set("metadata" -> union), false, false, WriteConcern.SAFE)
	    	  }
	    	  case None => {
	    	     FileDAO.dao.collection.update(MongoDBObject("_id" -> new ObjectId(id)), $set("metadata" -> doc), false, false, WriteConcern.SAFE)
	    	  }
	    	}
	      }
	      case None => {
	        Logger.error("Error getting file" + id)
		    NotFound
	      }
      } 
            
	 Logger.debug("Updating previews.files " + id + " with " + doc)
	 Ok(toJson("success"))
    }
  
  
  /**
   * Upload file using multipart form enconding.
   */
    def upload(showPreviews: String="FileLevel") = SecuredAction(parse.multipartFormData, authorization=WithPermission(Permission.CreateFiles)) {  implicit request =>
      request.user match {
        case Some(user) => {
	      request.body.file("File").map { f =>
	          var nameOfFile = f.filename
	          var flags = ""
	          if(nameOfFile.endsWith(".ptm")){
		          	  var thirdSeparatorIndex = nameOfFile.indexOf("__")
		              if(thirdSeparatorIndex >= 0){
		                var firstSeparatorIndex = nameOfFile.indexOf("_")
		                var secondSeparatorIndex = nameOfFile.indexOf("_", firstSeparatorIndex+1)
		            	flags = flags + "+numberofIterations_" +  nameOfFile.substring(0,firstSeparatorIndex) + "+heightFactor_" + nameOfFile.substring(firstSeparatorIndex+1,secondSeparatorIndex)+ "+ptm3dDetail_" + nameOfFile.substring(secondSeparatorIndex+1,thirdSeparatorIndex)
		            	nameOfFile = nameOfFile.substring(thirdSeparatorIndex+2)
		              }
	          }
	        
	        Logger.debug("Uploading file " + nameOfFile)
	        // store file
	        val file = Services.files.save(new FileInputStream(f.ref.file), nameOfFile, f.contentType, user, showPreviews)
	        val uploadedFile = f
	        file match {
	          case Some(f) => {
	            val id = f.id.toString
	            if(showPreviews.equals("None"))
	              flags = flags + "+nopreviews"
	            var fileType = f.contentType
	            if(fileType.contains("/zip") || fileType.contains("/x-zip") || nameOfFile.endsWith(".zip")){
	            	fileType = FilesUtils.getMainFileTypeOfZipFile(uploadedFile.ref.file, nameOfFile, "file")			          
	            	if(fileType.startsWith("ERROR: ")){
	            		Logger.error(fileType.substring(7))
	            		InternalServerError(fileType.substring(7))
	            	}			          
	            }else if(nameOfFile.endsWith(".mov")){
			        	fileType = "ambiguous/mov";
			        }    	

	            val key = "unknown." + "file."+ fileType.replace(".", "_").replace("/", ".")
	            		// TODO RK : need figure out if we can use https
	            val host = "http://" + request.host + request.path.replaceAll("api/files$", "")

	            current.plugin[RabbitmqPlugin].foreach{_.extract(ExtractorMessage(id, id, host, key, Map.empty, f.length.toString, "", flags))}
	             
	            current.plugin[ElasticsearchPlugin].foreach{
	              _.index("data", "file", id, List(("filename",nameOfFile), ("contentType", f.contentType)))
	            }
	            Ok(toJson(Map("id"->id)))   
	          }
	          case None => {
	            Logger.error("Could not retrieve file that was just saved.")
	            InternalServerError("Error uploading file")
	          }
	        }
	      }.getOrElse {
	         BadRequest(toJson("File not attached."))
	      }
        }
        
        case None => BadRequest(toJson("Not authorized."))
      }
    }
    
    
     /**
   * Send job for file preview(s) generation at a later time.
   */
    def sendJob(file_id: String, fileType: String) = SecuredAction(parse.anyContent, authorization=WithPermission(Permission.CreateFiles)) {  implicit request =>
          FileDAO.get(file_id) match {
		      case Some(theFile) => { 
		          var nameOfFile = theFile.filename
		          var flags = ""
		          if(nameOfFile.endsWith(".ptm")){
			          	  var thirdSeparatorIndex = nameOfFile.indexOf("__")
			              if(thirdSeparatorIndex >= 0){
			                var firstSeparatorIndex = nameOfFile.indexOf("_")
			                var secondSeparatorIndex = nameOfFile.indexOf("_", firstSeparatorIndex+1)
			            	flags = flags + "+numberofIterations_" +  nameOfFile.substring(0,firstSeparatorIndex) + "+heightFactor_" + nameOfFile.substring(firstSeparatorIndex+1,secondSeparatorIndex)+ "+ptm3dDetail_" + nameOfFile.substring(secondSeparatorIndex+1,thirdSeparatorIndex)
			            	nameOfFile = nameOfFile.substring(thirdSeparatorIndex+2)
			              }
		          }
		        
		        val showPreviews = theFile.showPreviews   
		          
		        Logger.debug("(Re)sending job for file " + nameOfFile)
		       
		            val id = theFile.id.toString
		            if(showPreviews.equals("None"))
		              flags = flags + "+nopreviews"   	
	
		            val key = "unknown." + "file."+ fileType.replace("__", ".")
		            		// TODO RK : need figure out if we can use https
		            val host = "http://" + request.host + request.path.replaceAll("api/files/sendJob/[A-Za-z0-9_]*/.*$", "")
	
		            current.plugin[RabbitmqPlugin].foreach{_.extract(ExtractorMessage(id, id, host, key, Map.empty, theFile.length.toString, "", flags))}
		             
//		            current.plugin[ElasticsearchPlugin].foreach{
//		              _.index("data", "file", id, List(("filename",nameOfFile), ("contentType", theFile.contentType)))
//		            }
		            Ok(toJson(Map("id"->id)))   
		          
		        }
		      case None => {
		         BadRequest(toJson("File not found."))
		      }
          }
    }
    
    
    
  /**
   * Upload a file to a specific dataset
   */
  def uploadToDataset(dataset_id: String, showPreviews: String="DatasetLevel") = SecuredAction(parse.multipartFormData, authorization=WithPermission(Permission.CreateFiles)) { implicit request =>
    request.user match {
        case Some(user) => {
    Services.datasets.get(dataset_id) match {
      case Some(dataset) => {
        request.body.file("File").map { f =>
          		var nameOfFile = f.filename
	            var flags = ""
	            if(nameOfFile.endsWith(".ptm")){
	            	  var thirdSeparatorIndex = nameOfFile.indexOf("__")
		              if(thirdSeparatorIndex >= 0){
		                var firstSeparatorIndex = nameOfFile.indexOf("_")
		                var secondSeparatorIndex = nameOfFile.indexOf("_", firstSeparatorIndex+1)
		            	flags = flags + "+numberofIterations_" +  nameOfFile.substring(0,firstSeparatorIndex) + "+heightFactor_" + nameOfFile.substring(firstSeparatorIndex+1,secondSeparatorIndex)+ "+ptm3dDetail_" + nameOfFile.substring(secondSeparatorIndex+1,thirdSeparatorIndex)
		            	nameOfFile = nameOfFile.substring(thirdSeparatorIndex+2)
		              }
	            }
          
          Logger.debug("Uploading file " + nameOfFile)
          // store file
          val file = Services.files.save(new FileInputStream(f.ref.file), nameOfFile, f.contentType, user, showPreviews)
          val uploadedFile = f
          
          // submit file for extraction
          file match {
            case Some(f) => {
              val id = f.id.toString
              if(showPreviews.equals("FileLevel"))
	            flags = flags + "+filelevelshowpreviews"
	          else if(showPreviews.equals("None"))
	            flags = flags + "+nopreviews"
	          var fileType = f.contentType
	          if(fileType.contains("/zip") || fileType.contains("/x-zip") || nameOfFile.endsWith(".zip")){
	        	  fileType = FilesUtils.getMainFileTypeOfZipFile(uploadedFile.ref.file, nameOfFile, "dataset")			          
	        	  if(fileType.startsWith("ERROR: ")){
	        		  Logger.error(fileType.substring(7))
	        		  InternalServerError(fileType.substring(7))
				  }			          
			  }else if(nameOfFile.endsWith(".mov")){
			        	fileType = "ambiguous/mov";
			        }
	              
	          // TODO RK need to replace unknown with the server name
	          val key = "unknown." + "file." + fileType.replace(".", "_").replace("/", ".")
	          // TODO RK : need figure out if we can use https
	          val host = "http://" + request.host + request.path.replaceAll("api/uploadToDataset/[A-Za-z0-9_]*$", "")
	              
	          current.plugin[RabbitmqPlugin].foreach { _.extract(ExtractorMessage(id, id, host, key, Map.empty, f.length.toString, dataset_id, flags)) }
                           
              current.plugin[ElasticsearchPlugin].foreach {
                _.index("files", "file", id, List(("filename", nameOfFile), ("contentType", f.contentType)))
              }

              // add file to dataset   
              // TODO create a service instead of calling salat directly
              Dataset.addFile(dataset.id.toString, f)

              // TODO RK need to replace unknown with the server name and dataset type
              val dtkey = "unknown." + "dataset." + "unknown"

              current.plugin[RabbitmqPlugin].foreach { _.extract(ExtractorMessage(dataset_id, dataset_id, host, dtkey, Map.empty, f.length.toString, dataset_id, "")) }

              Logger.info("Uploading Completed")

              //sending success message
              Ok(toJson(Map("id" -> id)))
            }
            case None => {
              Logger.error("Could not retrieve file that was just saved.")
              InternalServerError("Error uploading file")
            }
          }

          //Ok(views.html.multimediasearch())
        }.getOrElse {
          BadRequest("File not attached.")
        }
      } 
        case None => { Logger.error("Error getting dataset" + dataset_id); InternalServerError }
      }
     }
        
        case None => BadRequest(toJson("Not authorized."))
    }
   }

   /**
   * Upload intermediate file of extraction chain using multipart form enconding and continue chaining.
   */
    def uploadIntermediate(originalIdAndFlags: String) = SecuredAction(parse.multipartFormData, authorization=WithPermission(Permission.CreateFiles)) {  implicit request =>
      request.user match {
        case Some(user) => {
	      request.body.file("File").map { f =>
	        var originalId = originalIdAndFlags;
	        var flags = "";
	        if(originalIdAndFlags.indexOf("+") != -1){
	          originalId = originalIdAndFlags.substring(0,originalIdAndFlags.indexOf("+"));
	          flags = originalIdAndFlags.substring(originalIdAndFlags.indexOf("+"));
	        }
	        
	        Logger.debug("Uploading intermediate file " + f.filename + " associated with original file with id " + originalId)
	        // store file
	        val file = Services.files.save(new FileInputStream(f.ref.file), f.filename, f.contentType, user)	        
	        val uploadedFile = f
	        file match {
	          case Some(f) => {
	             FileDAO.setIntermediate(f.id.toString())
	             var fileType = f.contentType
			     if(fileType.contains("/zip") || fileType.contains("/x-zip") || f.filename.endsWith(".zip")){
			          fileType = FilesUtils.getMainFileTypeOfZipFile(uploadedFile.ref.file, f.filename, "file")			          
			          if(fileType.startsWith("ERROR: ")){
			             Logger.error(fileType.substring(7))
			             InternalServerError(fileType.substring(7))
			          }			          
			        }else if(f.filename.endsWith(".mov")){
			        	fileType = "ambiguous/mov";
			        } 
	            
	            val key = "unknown." + "file."+ fileType.replace(".","_").replace("/", ".")
	            // TODO RK : need figure out if we can use https
	            val host = "http://" + request.host + request.path.replaceAll("api/files/uploadIntermediate/[A-Za-z0-9_+]*$", "")
	            val id = f.id.toString
	            current.plugin[RabbitmqPlugin].foreach{_.extract(ExtractorMessage(originalId, id, host, key, Map.empty, f.length.toString, "", flags))}
	            current.plugin[ElasticsearchPlugin].foreach{
	              _.index("files", "file", id, List(("filename",f.filename), ("contentType", f.contentType)))
	            }
	            Ok(toJson(Map("id"->id)))   
	          }
	          case None => {
	            Logger.error("Could not retrieve file that was just saved.")
	            InternalServerError("Error uploading file")
	          }
	        }
	      }.getOrElse {
	         BadRequest(toJson("File not attached."))
	      }
	  }
        
      case None => BadRequest(toJson("Not authorized."))
    }
  }
    
  /**
   * Upload metadata for preview and attach it to a file.
   */  
  def uploadPreview(file_id: String) = SecuredAction(parse.multipartFormData, authorization=WithPermission(Permission.CreateFiles)) { implicit request =>
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
   * Add preview to file.
   */
  def attachPreview(file_id: String, preview_id: String) = SecuredAction(authorization=WithPermission(Permission.CreateFiles)) {  request =>
      request.body match {
        case JsObject(fields) => {
          // TODO create a service instead of calling salat directly
          FileDAO.findOneById(new ObjectId(file_id)) match { 
            case Some(file) => {
	              PreviewDAO.findOneById(new ObjectId(preview_id)) match {
	                case Some(preview) =>
	                    val metadata = fields.toMap.flatMap(tuple => MongoDBObject(tuple._1 -> tuple._2.as[String]))
	                    PreviewDAO.dao.collection.update(MongoDBObject("_id" -> new ObjectId(preview_id)), 
	                        $set("metadata"-> metadata, "file_id" -> new ObjectId(file_id)), false, false, WriteConcern.SAFE)      
	                    Logger.debug("Updating previews.files " + preview_id + " with " + metadata)
	                    Ok(toJson(Map("status"->"success")))
	                case None => BadRequest(toJson("Preview not found"))
	              }
            }
	        case None => BadRequest(toJson("File not found " + file_id))
	      }
        }
        case _ => Ok("received something else: " + request.body + '\n')
    }
    }
  
    def jsonFile(file: File): JsValue = {
        toJson(Map("id"->file.id.toString, "filename"->file.filename, "content-type"->file.contentType, "date-created"->file.uploadDate.toString(), "size"->file.length.toString))
  }
  
  def toDBObject(fields: Seq[(String, JsValue)]): DBObject = {
      fields.map(field =>
        field match {
          // TODO handle jsarray
//          case (key, JsArray(value: Seq[JsValue])) => MongoDBObject(key -> getValueForSeq(value))
          case (key, jsObject: JsObject) => MongoDBObject(key -> toDBObject(jsObject.fields))
          case (key, jsValue: JsValue) => MongoDBObject(key -> jsValue.as[String])
        }
      ).reduce((left:DBObject, right:DBObject) => left ++ right)
    }
  
  def filePreviewsList(id: String) = SecuredAction(parse.anyContent, authorization=WithPermission(Permission.CreateFiles)) {  request =>
			FileDAO.findOneById(new ObjectId(id)) match {
			case Some(file) => {
                val filePreviews = PreviewDAO.findByFileId(file.id);
				val list = for (prv <- filePreviews) yield jsonPreview(prv)
				Ok(toJson(list))       
			}
			case None => {Logger.error("Error getting file" + id); InternalServerError}
			}
		}
  
  def jsonPreview(preview: Preview): JsValue = {
    toJson(Map("id"->preview.id.toString, "filename"->getFilenameOrEmpty(preview), "contentType"->preview.contentType)) 
  }
  
   def getFilenameOrEmpty(preview : Preview): String = {    
    preview.filename match {
      case Some(strng) => strng
      case None => ""
    }   
  }
   
    /**
   * Add 3D geometry file to file.
   */
  def attachGeometry(file_id: String, geometry_id: String) = SecuredAction(authorization=WithPermission(Permission.CreateFiles)) {  request =>
      request.body match {
        case JsObject(fields) => {
          // TODO create a service instead of calling salat directly
          FileDAO.findOneById(new ObjectId(file_id)) match { 
            case Some(file) => {
	              GeometryDAO.findOneById(new ObjectId(geometry_id)) match {
	                case Some(geometry) =>
	                    val metadata = fields.toMap.flatMap(tuple => MongoDBObject(tuple._1 -> tuple._2.as[String]))
	                    GeometryDAO.dao.collection.update(MongoDBObject("_id" -> new ObjectId(geometry_id)), 
	                        $set("metadata"-> metadata, "file_id" -> new ObjectId(file_id)), false, false, WriteConcern.SAFE)
	                    Ok(toJson(Map("status"->"success")))
	                case None => BadRequest(toJson("Geometry file not found"))
	              }
            }
	        case None => BadRequest(toJson("File not found " + file_id))
	      }
        }
        case _ => Ok("received something else: " + request.body + '\n')
    }
   }
  
  
   /**
   * Add 3D texture to file.
   */
  def attachTexture(file_id: String, texture_id: String) = SecuredAction(authorization=WithPermission(Permission.CreateFiles)) {  request =>
      request.body match {
        case JsObject(fields) => {
          // TODO create a service instead of calling salat directly
          FileDAO.findOneById(new ObjectId(file_id)) match { 
            case Some(file) => {
	              ThreeDTextureDAO.findOneById(new ObjectId(texture_id)) match {
	                case Some(texture) =>
	                    val metadata = fields.toMap.flatMap(tuple => MongoDBObject(tuple._1 -> tuple._2.as[String]))
	                    ThreeDTextureDAO.dao.collection.update(MongoDBObject("_id" -> new ObjectId(texture_id)), 
	                        $set("metadata"-> metadata, "file_id" -> new ObjectId(file_id)), false, false, WriteConcern.SAFE)
	                    Ok(toJson(Map("status"->"success")))
	                case None => BadRequest(toJson("Texture file not found"))
	              }
            }
	        case None => BadRequest(toJson("File not found " + file_id))
	      }
        }
        case _ => Ok("received something else: " + request.body + '\n')
    }
   }
  
  /**
   * Add thumbnail to file.
   */
  def attachThumbnail(file_id: String, thumbnail_id: String) = SecuredAction(parse.anyContent, authorization=WithPermission(Permission.CreateFiles)) { implicit  request =>
          // TODO create a service instead of calling salat directly
          FileDAO.findOneById(new ObjectId(file_id)) match { 
            case Some(file) => {
	              models.Thumbnail.findOneById(new ObjectId(thumbnail_id)) match {
	                case Some(thumbnail) =>{
	                    FileDAO.dao.collection.update(MongoDBObject("_id" -> new ObjectId(file_id)), 
	                        $set("thumbnail_id" -> new ObjectId(thumbnail_id)), false, false, WriteConcern.SAFE)
	                        
	                    Dataset.findOneByFileId(file.id) match {
	                      case Some(dataset) => {
	                        if(dataset.thumbnail_id.isEmpty)
		                        Dataset.dao.collection.update(MongoDBObject("_id" -> dataset.id), 
		                        $set("thumbnail_id" -> new ObjectId(thumbnail_id)), false, false, WriteConcern.SAFE)
	                      }
	                      case None =>
	                    }
	                        
	                    Ok(toJson(Map("status"->"success")))
	                }
	                case None => BadRequest(toJson("Thumbnail not found"))
	              }
            }
	        case None => BadRequest(toJson("File not found " + file_id))
	      }       
   }
  
   /**
   * Find geometry file for given 3D file and geometry filename.
   */
  def getGeometry(three_d_file_id: String, filename: String) =
    SecuredAction(parse.anyContent, authorization=WithPermission(Permission.ShowFile)) { request => 
      GeometryDAO.findGeometry(new ObjectId(three_d_file_id), filename) match {
        case Some(geometry) => {
          
          GeometryDAO.getBlob(geometry.id.toString()) match {
            
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
	      case None => Logger.error("No geometry file found: " + geometry.id.toString()); InternalServerError("No geometry file found")
            
          }
          
        }         
        case None => Logger.error("Geometry file not found"); InternalServerError
      }
    }
   
    /**
   * Find texture file for given 3D file and texture filename.
   */
  def getTexture(three_d_file_id: String, filename: String) =
    SecuredAction(parse.anyContent, authorization=WithPermission(Permission.ShowFile)) { request => 
      ThreeDTextureDAO.findTexture(new ObjectId(three_d_file_id), filename) match {
        case Some(texture) => {
          
          ThreeDTextureDAO.getBlob(texture.id.toString()) match {
            
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
	      case None => Logger.error("No texture file found: " + texture.id.toString()); InternalServerError("No texture found")
            
          }
          
        }         
        case None => Logger.error("Texture file not found"); InternalServerError
      }
    }
   
    def tag(id: String) = SecuredAction(authorization=WithPermission(Permission.CreateTags)) { implicit request =>
	    request.body.\("tag").asOpt[String] match {
		    case Some(tag) => {
		    	FileDAO.tag(id, tag)
		    	Ok
		    }
		    case None => {
		    	Logger.error("no tag specified.")
		    	BadRequest
		    }
	    }
    }

	def comment(id: String) = SecuredAction(authorization=WithPermission(Permission.CreateComments))  { implicit request =>
	  request.user match {
	    case Some(identity) => {
		    request.body.\("text").asOpt[String] match {
			    case Some(text) => {
			        val comment = new Comment(identity, text, file_id=Some(id))
			        Comment.save(comment)
			        Ok(comment.id.toString())
			    }
			    case None => {
			    	Logger.error("no text specified.")
			    	BadRequest
			    }
		    }
	    }
	    case None => BadRequest
	  
	
	
  /**
   * Return whether a file is currently being processed.
   */
  def isBeingProcessed(id: String) = SecuredAction(parse.anyContent, authorization=WithPermission(Permission.ShowFile)) { request =>
  	Services.files.getFile(id) match {
  	  case Some(file) => { 	    
  		  		  var isActivity = "false"
  				  Extraction.findIfBeingProcessed(file.id) match{
	  				  case false => 
	  				  case true => { 
        				isActivity = "true"
        			  } 
  		  		  }	
        
        Ok(toJson(Map("isBeingProcessed"->isActivity))) 
  	  }
  	  case None => {Logger.error("Error getting file" + id); InternalServerError}
  	}  	
  }
	
	
   def jsonPreviewsFiles(filesList: List[(models.File, Array[(java.lang.String, String, String, String, java.lang.String, String, Long)])]): JsValue = {
    val list = for (filePrevs <- filesList) yield jsonPreviews(filePrevs._1, filePrevs._2)
    toJson(list)
  }  
  def jsonPreviews(prvFile: models.File, prvs: Array[(java.lang.String, String, String, String, java.lang.String, String, Long)]): JsValue = {
    val list = for (prv <- prvs) yield jsonPreview(prv._1, prv._2, prv._3, prv._4, prv._5, prv._6, prv._7)
    val listJson = toJson(list.toList)
    toJson(Map[String, JsValue]("file_id" -> JsString(prvFile.id.toString), "previews" -> listJson))
  }
  def jsonPreview(pvId: java.lang.String, pId: String, pPath: String, pMain: String, pvRoute: java.lang.String, pvContentType: String, pvLength: Long): JsValue = {
    if(pId.equals("X3d"))
    	toJson(Map("pv_id" -> pvId, "p_id" -> pId, "p_path" -> controllers.routes.Assets.at(pPath).toString, "p_main" -> pMain, "pv_route" -> pvRoute, "pv_contenttype" -> pvContentType, "pv_length" -> pvLength.toString,
    			"pv_annotationsEditPath" -> api.routes.Previews.editAnnotation(pvId).toString, "pv_annotationsListPath" -> api.routes.Previews.listAnnotations(pvId).toString, "pv_annotationsAttachPath" -> api.routes.Previews.attachAnnotation(pvId).toString)) 
    else    
    	toJson(Map("pv_id" -> pvId, "p_id" -> pId, "p_path" -> controllers.routes.Assets.at(pPath).toString , "p_main" -> pMain, "pv_route" -> pvRoute, "pv_contenttype" -> pvContentType, "pv_length" -> pvLength.toString))  
  }  
  def getPreviews(id: String) = SecuredAction(parse.anyContent, authorization=WithPermission(Permission.ShowFile)) { request =>
    Services.files.getFile(id)  match {
      case Some(file) => {
        
        val previewsFromDB = PreviewDAO.findByFileId(file.id)        
        val previewers = Previewers.findPreviewers
        //Logger.info("Number of previews " + previews.length);
        val files = List(file)        
         val previewslist = for(f <- files; if(!f.showPreviews.equals("None"))) yield {
          val pvf = for(p <- previewers ; pv <- previewsFromDB; if (p.contentType.contains(pv.contentType))) yield {            
            (pv.id.toString, p.id, p.path, p.main, api.routes.Previews.download(pv.id.toString).toString, pv.contentType, pv.length)
          }        
          if (pvf.length > 0) {
            (file -> pvf)
          } else {
  	        val ff = for(p <- previewers ; if (p.contentType.contains(file.contentType))) yield {
  	          (file.id.toString, p.id, p.path, p.main, controllers.routes.Files.file(file.id.toString) + "/blob", file.contentType, file.length)
  	        }
  	        (file -> ff)
          }
        }

        Ok(jsonPreviewsFiles(previewslist.asInstanceOf[List[(models.File, Array[(java.lang.String, String, String, String, java.lang.String, String, Long)])]])) 
      }
      case None => {Logger.error("Error getting file" + id); InternalServerError}
    }
  }
  
  def removeFile(id: String) = SecuredAction(parse.anyContent, authorization=WithPermission(Permission.DeleteFiles)) { request =>
    Services.files.getFile(id)  match {
      case Some(file) => {
        FileDAO.removeFile(id)
        Ok(toJson(Map("status"->"success")))
      }
      case None => Ok(toJson(Map("status"->"success")))
    }
  }
  
	
}
