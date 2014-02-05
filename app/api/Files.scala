/**
 *
 */
package api

import java.io.FileInputStream
import java.util.Date
import java.io.BufferedWriter
import java.io.FileWriter
import java.io.ByteArrayInputStream

import scala.collection.mutable.MutableList

import org.bson.types.ObjectId

import com.mongodb.WriteConcern
import com.mongodb.casbah.Imports._

import models._
import play.api.Logger
import play.api.Play.current
import play.api.libs.concurrent.Execution.Implicits._
import play.api.libs.iteratee.Enumerator
import play.api.libs.json._
import play.api.libs.json.JsValue
import play.api.libs.json.Json._
import play.api.mvc.{SimpleResult, Action, Controller}
import services._
import javax.inject.{ Singleton, Inject }
import play.api.libs.concurrent.Execution.Implicits._

import scala.collection.mutable.ListBuffer
import scala.util.parsing.json.JSONArray

import org.json.JSONObject
import org.json.XML

import Transformation.LidoToCidocConvertion

import jsonutils.JsonUtil
import play.api.libs.json.JsString
import scala.Some
import services.ExtractorMessage
import scala.util.parsing.json.JSONArray
import api.RequestWithUser
import api.WithPermission
import play.api.libs.json.JsObject
import fileutils.FilesUtils
import play.api.libs.json.JsString
import scala.Some
import services.DumpOfFile
import services.ExtractorMessage
import scala.util.parsing.json.JSONArray
import api.RequestWithUser
import api.WithPermission
import controllers.Previewers
import scala.concurrent.Future
 
import scala.util.control._

/**
 * Json API for files.
 *
 * @author Luigi Marini
 *
 */
class Files @Inject() (files: FileService, datasets: DatasetService, queries: QueryService, tags: TagService)  extends ApiController {
  
  def get(id: String) = SecuredAction(parse.anyContent, authorization=WithPermission(Permission.ShowFile)) { implicit request =>
	    Logger.info("GET file with id " + id)    
	    files.getFile(id) match {
	      case Some(file) => Ok(jsonFile(file))
	      case None => {Logger.error("Error getting file" + id); InternalServerError}
	    }
  }
  
  /**
   * List all files.
   */
  def list = SecuredAction(parse.anyContent, authorization=WithPermission(Permission.ListFiles)) { request =>
      val list = for (f <- files.listFiles()) yield jsonFile(f)
      Ok(toJson(list))
    }
  
  def downloadByDatasetAndFilename(datasetId: String, filename: String, preview_id: String) = 
    SecuredAction(parse.anyContent, authorization=WithPermission(Permission.DownloadFiles)){ request =>
      datasets.getFileId(datasetId, filename) match{
        case Some(id) => { 
          Redirect(routes.Files.download(id)) 
        }
       case None => { Logger.error("Error getting dataset" + datasetId); InternalServerError }
    }  
  }
  
  
  /**
   * Download file using http://en.wikipedia.org/wiki/Chunked_transfer_encoding
   */
  def download(id: String) = 
	    SecuredAction(parse.anyContent, authorization=WithPermission(Permission.DownloadFiles)) { request =>

		    files.get(id) match {
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
		            Ok.chunked(Enumerator.fromStream(inputStream))
		            	.withHeaders(CONTENT_TYPE -> contentType)
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
   * 
   * Download query used by Versus
   * 
   */
  def downloadquery(id: String) = 
	    SecuredAction(parse.anyContent, authorization=WithPermission(Permission.DownloadFiles)) { request =>
		    queries.get(id) match {
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
		            Ok.chunked(Enumerator.fromStream(inputStream))
		            	.withHeaders(CONTENT_TYPE -> contentType)
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
	    		  FileDAO.dao.collection.update(MongoDBObject("_id" -> new ObjectId(id)), $addToSet("metadata" -> doc), false, false, WriteConcern.SAFE)
	    		  index(id)
	      }
	      case None => {
	        Logger.error("Error getting file" + id)
		    NotFound
	      }
      } 
            
	 Logger.debug("Updating previews.files " + id + " with " + doc)
	 Ok(toJson("success"))
    }

 /***
  * For DTS service use case: suppose a user posts a file to the extraction api, no extractors are avaliable. Now she checks the status 
  * for the extractor if it up. If yes, she may again wants to submit the file for extraction again. Since she has already uploaded
  * it, this time will just uses the file id to submit the request again.
  * This api takes file id and notify the user that the request has been sent for processing.
  * This may change depending on our our design on DTS extraction service. 
  * 
  */
  def submitAextraction(id:String)=SecuredAction(parse.anyContent, authorization = WithPermission(Permission.ShowFile)){ implicit request =>
    current.plugin[RabbitmqPlugin] match {
        case Some(plugin) => {
          if (ObjectId.isValid(id)) {
            files.getFile(id) match {
              case Some(file) => {
                val fileType=file.contentType
                val key = "unknown." + "file." + fileType.replace(".", "_").replace("/", ".")
                val host = "http://" + request.host
                current.plugin[RabbitmqPlugin].foreach { _.extract(ExtractorMessage(id, id, host, key, Map.empty, file.length.toString, "", "")) }
                Ok("Sent for Extraction. check the status")
              }
              case None=>
                Logger.error("Could not retrieve file that was just saved.")
              InternalServerError("Error uploading file")
            }//file match
          } // if Object id
          else{
            BadRequest("Not valid id")
          }
        } //case plugin  
        case None=>{
          BadRequest("No Service")
        }
    }//plugin match         
   }
  
  
  /**
   * Uploads file for extraction and returns a file id ; It does not index the file. 
   * This is very similar to upload(). 
   * Needs to be decided on the semantics of upload for DTS extraction service and its difference to upload file to Medici for curation and storage.   
   * This may change accordingly.
   */

  def uploadExtract() = SecuredAction(parse.multipartFormData, authorization = WithPermission(Permission.CreateFiles)) { implicit request =>
    request.user match {
      case Some(user) => {
        request.body.file("File").map { f =>
          var nameOfFile = f.filename
          var flags = ""
          if (nameOfFile.toLowerCase().endsWith(".ptm")) {
            var thirdSeparatorIndex = nameOfFile.indexOf("__")
            if (thirdSeparatorIndex >= 0) {
              var firstSeparatorIndex = nameOfFile.indexOf("_")
              var secondSeparatorIndex = nameOfFile.indexOf("_", firstSeparatorIndex + 1)
              flags = flags + "+numberofIterations_" + nameOfFile.substring(0, firstSeparatorIndex) + "+heightFactor_" + nameOfFile.substring(firstSeparatorIndex + 1, secondSeparatorIndex) + "+ptm3dDetail_" + nameOfFile.substring(secondSeparatorIndex + 1, thirdSeparatorIndex)
              nameOfFile = nameOfFile.substring(thirdSeparatorIndex + 2)
            }
          }

          Logger.debug("Uploading file " + nameOfFile)
          // store file
          val file = files.save(new FileInputStream(f.ref.file), nameOfFile, f.contentType, user, null)
          val uploadedFile = f
          file match {
            case Some(f) => {
              current.plugin[FileDumpService].foreach { _.dump(DumpOfFile(uploadedFile.ref.file, f.id.toString, nameOfFile)) }

              val id = f.id.toString

              var fileType = f.contentType
              if (fileType.contains("/zip") || fileType.contains("/x-zip") || nameOfFile.toLowerCase().endsWith(".zip")) {
                fileType = FilesUtils.getMainFileTypeOfZipFile(uploadedFile.ref.file, nameOfFile, "file")
                if (fileType.startsWith("ERROR: ")) {
                  Logger.error(fileType.substring(7))
                  InternalServerError(fileType.substring(7))
                }
              }

              val key = "unknown." + "file." + fileType.replace(".", "_").replace("/", ".")
              // TODO RK : need figure out if we can use https
              Logger.debug("request.hosts=" + request.host)
              Logger.debug("request.path=" + request.path)
              val host = "http://" + request.host
              current.plugin[RabbitmqPlugin].foreach { _.extract(ExtractorMessage(id, id, host, key, Map.empty, f.length.toString, "", flags)) }

              //for metadata files
              if (fileType.equals("application/xml") || fileType.equals("text/xml")) {
                val xmlToJSON = FilesUtils.readXMLgetJSON(uploadedFile.ref.file)
                FileDAO.addXMLMetadata(id, xmlToJSON)

                Logger.debug("xmlmd=" + xmlToJSON)

              }

              Ok(toJson(Map("id" -> id)))
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
   * Upload file using multipart form enconding.
   */
  
    def upload(showPreviews: String="DatasetLevel") = SecuredAction(parse.multipartFormData, authorization=WithPermission(Permission.CreateFiles)) {  implicit request =>
      request.user match {
        case Some(user) => {
	      request.body.file("File").map { f =>        
	          var nameOfFile = f.filename
	          var flags = ""
	          if(nameOfFile.toLowerCase().endsWith(".ptm")){
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
	        val file = files.save(new FileInputStream(f.ref.file), nameOfFile, f.contentType, user, showPreviews)
	        val uploadedFile = f
	        file match {
	          case Some(f) => {
	            current.plugin[FileDumpService].foreach{_.dump(DumpOfFile(uploadedFile.ref.file, f.id.toString, nameOfFile))}
	            
	            val id = f.id.toString
	            if(showPreviews.equals("FileLevel"))
	            	flags = flags + "+filelevelshowpreviews"
	            else if(showPreviews.equals("None"))
	            	flags = flags + "+nopreviews"
	            var fileType = f.contentType
	            if(fileType.contains("/zip") || fileType.contains("/x-zip") || nameOfFile.toLowerCase().endsWith(".zip")){
	            	fileType = FilesUtils.getMainFileTypeOfZipFile(uploadedFile.ref.file, nameOfFile, "file")
			          if(fileType.startsWith("ERROR: ")){
			             Logger.error(fileType.substring(7))
			             InternalServerError(fileType.substring(7))
			          }			          
			        }    	
	            
	            val key = "unknown." + "file."+ fileType.replace(".", "_").replace("/", ".")
	            // TODO RK : need figure out if we can use https
	            val host = "http://" + request.host + request.path.replaceAll("api/files$", "")

	            current.plugin[RabbitmqPlugin].foreach{_.extract(ExtractorMessage(id, id, host, key, Map.empty, f.length.toString, "", flags))}
	            	            
	            //for metadata files
	            if(fileType.equals("application/xml") || fileType.equals("text/xml")){
	              val xmlToJSON = FilesUtils.readXMLgetJSON(uploadedFile.ref.file)
	              FileDAO.addXMLMetadata(id, xmlToJSON)
	              
	              Logger.debug("xmlmd=" + xmlToJSON)
	              
	              current.plugin[ElasticsearchPlugin].foreach{
		              _.index("data", "file", id, List(("filename",nameOfFile), ("contentType", f.contentType), ("xmlmetadata", xmlToJSON)))
		            }
	            }
	            else{
		            current.plugin[ElasticsearchPlugin].foreach{
		              _.index("data", "file", id, List(("filename",nameOfFile), ("contentType", f.contentType)))
		            }
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
		          if(nameOfFile.toLowerCase().endsWith(".ptm")){
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
    datasets.get(dataset_id) match {
      case Some(dataset) => {
        request.body.file("File").map { f =>
          		var nameOfFile = f.filename
	            var flags = ""
	            if(nameOfFile.toLowerCase().endsWith(".ptm")){
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
          val file = files.save(new FileInputStream(f.ref.file), nameOfFile, f.contentType, user, showPreviews)
          val uploadedFile = f
          
          // submit file for extraction
          file match {
            case Some(f) => {
              current.plugin[FileDumpService].foreach{_.dump(DumpOfFile(uploadedFile.ref.file, f.id.toString, nameOfFile))}
              
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
						  }
              
              // TODO RK need to replace unknown with the server name
              val key = "unknown." + "file." + fileType.replace(".", "_").replace("/", ".")
              // TODO RK : need figure out if we can use https
              val host = "http://" + request.host + request.path.replaceAll("api/uploadToDataset/[A-Za-z0-9_]*$", "")
	              
	          current.plugin[RabbitmqPlugin].foreach { _.extract(ExtractorMessage(id, id, host, key, Map.empty, f.length.toString, dataset_id, flags)) }
	          
	          //for metadata files
              if(fileType.equals("application/xml") || fileType.equals("text/xml")){
            	  		  val xmlToJSON = FilesUtils.readXMLgetJSON(uploadedFile.ref.file)
            			  FileDAO.addXMLMetadata(id, xmlToJSON)

            			  Logger.debug("xmlmd=" + xmlToJSON)

            			  current.plugin[ElasticsearchPlugin].foreach{
            		  		_.index("data", "file", id, List(("filename",f.filename), ("contentType", f.contentType),("datasetId",dataset.id.toString),("datasetName",dataset.name), ("xmlmetadata", xmlToJSON)))
            	  		  }
              }
              else{
            	  current.plugin[ElasticsearchPlugin].foreach{
            		  _.index("data", "file", id, List(("filename",f.filename), ("contentType", f.contentType),("datasetId",dataset.id.toString),("datasetName",dataset.name)))
            	  }
              }
                           
              // add file to dataset   
              // TODO create a service instead of calling salat directly
              val theFile = FileDAO.get(f.id.toString).get
              Dataset.addFile(dataset.id.toString, theFile)              
              if(!theFile.xmlMetadata.isEmpty){
	            	Dataset.index(dataset_id)
		      	}	

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

        }.getOrElse {
          BadRequest(toJson("File not attached."))
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
	        val file = files.save(new FileInputStream(f.ref.file), f.filename, f.contentType, user)	        
	        val uploadedFile = f
	        file match {
	          case Some(f) => {
	             FileDAO.setIntermediate(f.id.toString)
	             var fileType = f.contentType
			     if(fileType.contains("/zip") || fileType.contains("/x-zip") || f.filename.toLowerCase().endsWith(".zip")){
			          fileType = FilesUtils.getMainFileTypeOfZipFile(uploadedFile.ref.file, f.filename, "file")			          
			          if(fileType.startsWith("ERROR: ")){
			             Logger.error(fileType.substring(7))
			             InternalServerError(fileType.substring(7))
			          }			          
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
  def attachPreview(file_id: String, preview_id: String) = SecuredAction(authorization = WithPermission(Permission.CreateFiles)) { request =>
    // Use the "extractor_id" field contained in the POST data.  Use "Other" if absent.
    val eid = request.body.\("extractor_id").asOpt[String]
    val extractor_id = if (eid.isDefined) {
      eid
    } else {
      Logger.info("api.Files.attachPreview(): No \"extractor_id\" specified in request, set it to \"Other\".  request.body: " + request.body.toString)
      "Other"
    }
    request.body match {
      case JsObject(fields) => {
        // TODO create a service instead of calling salat directly
        FileDAO.findOneById(new ObjectId(file_id)) match {
          case Some(file) => {
            PreviewDAO.findOneById(new ObjectId(preview_id)) match {
              case Some(preview) =>
                // "extractor_id" is stored at the top level of "Preview".  Remove it from the "metadata" field to avoid dup.
                val metadata = (fields.toMap - "extractor_id").flatMap(tuple => MongoDBObject(tuple._1 -> tuple._2.as[String]))
                PreviewDAO.dao.collection.update(MongoDBObject("_id" -> new ObjectId(preview_id)),
                  $set("metadata" -> metadata, "file_id" -> new ObjectId(file_id), "extractor_id" -> extractor_id),
                  false, false, WriteConcern.SAFE)
                Logger.debug("Updating previews.files " + preview_id + " with " + metadata)
                Ok(toJson(Map("status" -> "success")))
              case None => BadRequest(toJson("Preview not found"))
            }
          }
          //If file to be previewed is not found, just delete the preview 
          case None => {
            PreviewDAO.findOneById(new ObjectId(preview_id)) match {
              case Some(preview) =>
                Logger.debug("File not found. Deleting previews.files " + preview_id)
                PreviewDAO.removePreview(preview)
                BadRequest(toJson("File not found. Preview deleted."))
              case None => BadRequest(toJson("Preview not found"))
            }
          }
        }
      }
      case _ => Ok("received something else: " + request.body + '\n')
    }
  }
  
  def getRDFUserMetadata(id: String, mappingNumber: String="1") = SecuredAction(parse.anyContent, authorization=WithPermission(Permission.ShowFilesMetadata)) {implicit request =>
    files.getFile(id) match {
            case Some(file) => {
              val theJSON = FileDAO.getUserMetadataJSON(id)
              val fileSep = System.getProperty("file.separator")
	          var resultDir = play.api.Play.configuration.getString("rdfdumptemporary.dir").getOrElse("") + fileSep + new ObjectId().toString
	          new java.io.File(resultDir).mkdir()
              
              if(!theJSON.replaceAll(" ","").equals("{}")){
	              val xmlFile = jsonToXML(theJSON)
	              new LidoToCidocConvertion(play.api.Play.configuration.getString("filesxmltordfmapping.dir_"+mappingNumber).getOrElse(""), xmlFile.getAbsolutePath(), resultDir)	                            
	              xmlFile.delete()
              }
              else{
                new java.io.File(resultDir + fileSep + "Results.rdf").createNewFile()
              }
              val resultFile = new java.io.File(resultDir + fileSep + "Results.rdf")
              
              Ok.chunked(Enumerator.fromStream(new FileInputStream(resultFile)))
		            	.withHeaders(CONTENT_TYPE -> "application/rdf+xml")
		            	.withHeaders(CONTENT_DISPOSITION -> ("attachment; filename=" + resultFile.getName()))
            }
            case None => BadRequest(toJson("File not found " + id))
    }
  
  }
  
  def jsonToXML(theJSON: String): java.io.File = {
    
    val jsonObject = new JSONObject(theJSON)    
    var xml = org.json.XML.toString(jsonObject)
    
    //Remove spaces from XML tags
    var currStart = xml.indexOf("<")
    var currEnd = -1
    var xmlNoSpaces = ""
    while(currStart != -1){
      xmlNoSpaces = xmlNoSpaces + xml.substring(currEnd+1,currStart)
      currEnd = xml.indexOf(">", currStart+1)
      xmlNoSpaces = xmlNoSpaces + xml.substring(currStart,currEnd+1).replaceAll(" ", "_")
      currStart = xml.indexOf("<", currEnd+1)
    }
    xmlNoSpaces = xmlNoSpaces + xml.substring(currEnd+1)
    
    val xmlFile = java.io.File.createTempFile("xml",".xml")
    val fileWriter =  new BufferedWriter(new FileWriter(xmlFile))
    fileWriter.write(xmlNoSpaces)
    fileWriter.close()
    
    return xmlFile    
  }
  
  def getRDFURLsForFile(id: String) = SecuredAction(parse.anyContent, authorization=WithPermission(Permission.ShowFilesMetadata)) { request =>
    files.getFile(id)  match {
      case Some(file) => {
        
        //RDF from XML of the file itself (for XML metadata-only files)
        val previewsList = PreviewDAO.findByFileId(new ObjectId(id))
        var rdfPreviewList = List.empty[models.Preview]
        for(currPreview <- previewsList){
          if(currPreview.contentType.equals("application/rdf+xml")){
            rdfPreviewList = rdfPreviewList :+ currPreview
          }
        }        
        var hostString = "http://" + request.host + request.path.replaceAll("files/getRDFURLsForFile/[A-Za-z0-9_]*$", "previews/")
        var list = for (currPreview <- rdfPreviewList) yield Json.toJson(hostString + currPreview.id.toString)
        
        //RDF from export of file community-generated metadata to RDF
        var connectionChars = ""
        if(hostString.contains("?")){
          connectionChars = "&mappingNum="
        }
        else{
          connectionChars = "?mappingNum="
        }
        hostString = "http://" + request.host + request.path.replaceAll("/getRDFURLsForFile/", "/rdfUserMetadata/") + connectionChars                
        val mappingsQuantity = Integer.parseInt(play.api.Play.configuration.getString("filesxmltordfmapping.dircount").getOrElse("1"))
        for(i <- 1 to mappingsQuantity){
          var currHostString = hostString + i
          list = list :+ Json.toJson(currHostString)
        }

        val listJson = toJson(list.toList)
        
        Ok(listJson) 
      }
      case None => {Logger.error("Error getting file" + id); InternalServerError}
    }
    
  }
  
  def addUserMetadata(id: String) = SecuredAction(authorization=WithPermission(Permission.AddFilesMetadata)) {implicit request =>
	      Logger.debug("Adding user metadata to file " + id)
	      val theJSON = Json.stringify(request.body)
	      FileDAO.addUserMetadata(id, theJSON)
	      index(id)
	      Ok(toJson(Map("status" -> "success")))

  }
  
  def jsonFile(file: File): JsValue = {
        toJson(Map("id"->file.id.toString, "filename"->file.filename, "content-type"->file.contentType, "date-created"->file.uploadDate.toString(), "size"->file.length.toString))
  }
  
  def jsonFileWithThumbnail(file: File): JsValue = {
    var fileThumbnail = "None"
    if(!file.thumbnail_id.isEmpty)
      fileThumbnail = file.thumbnail_id.toString().substring(5,file.thumbnail_id.toString().length-1)
    
        toJson(Map("id"->file.id.toString, "filename"->file.filename, "contentType"->file.contentType, "dateCreated"->file.uploadDate.toString(), "thumbnail" -> fileThumbnail))
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
  
  def filePreviewsList(id: String) = SecuredAction(parse.anyContent, authorization=WithPermission(Permission.ShowFile)) {  request =>
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
  *  Add 3D geometry file to file.
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
	                        
	                    val datasetList = Dataset.findByFileId(file.id)
	                    for(dataset <- datasetList){
	                      if(dataset.thumbnail_id.isEmpty){
		                        Dataset.dao.collection.update(MongoDBObject("_id" -> dataset.id), 
		                        $set("thumbnail_id" -> new ObjectId(thumbnail_id)), false, false, WriteConcern.SAFE)		                        
		                    }
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
	            Ok.chunked(Enumerator.fromStream(inputStream))
	            	.withHeaders(CONTENT_TYPE -> contentType)
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

  // ---------- Tags related code starts ------------------
  /**
   *  REST endpoint: GET: get the tag data associated with this file.
   */
  def getTags(id: String) = SecuredAction(parse.anyContent, authorization = WithPermission(Permission.ShowFile)) { implicit request =>
    Logger.info("Getting tags for file with id " + id)
    /* Found in testing: given an invalid ObjectId, a runtime exception
     * ("IllegalArgumentException: invalid ObjectId") occurs in Services.files.getFile().
     * So check it first.
     */
    if (ObjectId.isValid(id)) {
      files.getFile(id) match {
        case Some(file) => Ok(Json.obj("id" -> file.id.toString, "filename" -> file.filename,
          "tags" -> Json.toJson(file.tags.map(_.name).distinct)))
        case None => {
          Logger.error("The file with id " + id + " is not found.")
          NotFound(toJson("The file with id " + id + " is not found."))
        }
      }
    } else {
      Logger.error("The given id " + id + " is not a valid ObjectId.")
      BadRequest(toJson("The given id " + id + " is not a valid ObjectId."))
    }
  }

  /*
   *  Helper function to handle adding and removing tags for files/datasets/sections.
   *  Input parameters:
   *      obj_type: one of the three TagCheckObjType's: TagCheck_File, TagCheck_Dataset or TagCheck_Section
   *      op_type:  one of the two strings "add", "remove"
   *      id:       the id in the original addTags call
   *      request:  the request in the original addTags call
   *  Return type:
   *      play.api.mvc.SimpleResult[JsValue]
   *      in the form of Ok, NotFound and BadRequest
   *      where: Ok contains the JsObject: "status" -> "success", the other two contain a JsString,
   *      which contains the cause of the error, such as "No 'tags' specified", and
   *      "The file with id 5272d0d7e4b0c4c9a43e81c8 is not found".
   */
  def addTagsHelper(obj_type: TagCheckObjType, id: String, request: RequestWithUser[JsValue]): SimpleResult = {

    val (not_found, error_str) = tags.addTagsHelper(obj_type, id, request)

    // Now the real work: adding the tags.
    if ("" == error_str) {
      Ok(Json.obj("status" -> "success"))
    } else {
      Logger.error(error_str)
      if (not_found) {
        NotFound(toJson(error_str))
      } else {
        BadRequest(toJson(error_str))
      }
    }
  }

  def removeTagsHelper(obj_type: TagCheckObjType, id: String, request: RequestWithUser[JsValue]): SimpleResult = {

    val (not_found, error_str) = tags.removeTagsHelper(obj_type, id, request)

    if ("" == error_str) {
      Ok(Json.obj("status" -> "success"))
    } else {
      Logger.error(error_str)
      if (not_found) {
        NotFound(toJson(error_str))
      } else {
        BadRequest(toJson(error_str))
      }
    }
  }

  /**
   *  REST endpoint: POST: Add tags to a file.
   *  Tag's (name, userId, extractor_id) tuple is used as a unique key.
   *  In other words, the same tag names but diff userId or extractor_id are considered as diff tags,
   *  so will be added.
   */
  def addTags(id: String) = SecuredAction(authorization = WithPermission(Permission.CreateTags)) { implicit request =>
  	addTagsHelper(TagCheck_File, id, request)
  }

  /**
   *  REST endpoint: POST: remove tags.
   *  Tag's (name, userId, extractor_id) tuple is used as a unique key.
   *  In other words, the same tag names but diff userId or extractor_id are considered as diff tags.
   *  Current implementation enforces the restriction which only allows the tags to be removed by
   *  the same user or extractor.
   */
  def removeTags(id: String) = SecuredAction(authorization = WithPermission(Permission.DeleteTags)) { implicit request =>
  	removeTagsHelper(TagCheck_File, id, request)
  }

  /**
   *  REST endpoint: POST: remove all tags.
   *  This is a big hammer -- it does not check the userId or extractor_id and
   *  forcefully remove all tags for this id.  It is mainly intended for testing. 
   */
  def removeAllTags(id: String) = SecuredAction(authorization = WithPermission(Permission.DeleteTags)) { implicit request =>
    Logger.info("Removing all tags for file with id: " + id)
    if (ObjectId.isValid(id)) {
      files.getFile(id) match {
        case Some(file) => {
          FileDAO.removeAllTags(id)
          Ok(Json.obj("status" -> "success"))
        }
        case None => {
          Logger.error("The file with id " + id + " is not found.")
          NotFound(toJson("The file with id " + id + " is not found."))
        }
      }
    } else {
      Logger.error("The given id " + id + " is not a valid ObjectId.")
      BadRequest(toJson("The given id " + id + " is not a valid ObjectId."))
    }
  }
  // ---------- Tags related code ends ------------------

  // ---------- Extract (BD-38) related code starts ------------------
  /**
   * Returns extracted lists of tags for a file
   * 
   */
  
  def extractTags(file: models.File) = {
    val tags = file.tags
    // Transform the tag list into a list of ["extractor_id" or "userId", "values"] items,
    // where "values" are the list of tag name values.
    var tagResE = Map[String, MutableList[String]]()
    tags.filter(_.extractor_id.isDefined).foreach(tag => {
      val ename = tag.extractor_id.get
      if (tagResE.contains(ename)) {
        tagResE.get(ename).get += tag.name
      } else {
        tagResE += ((ename, MutableList(tag.name)))
      }
    })
    val jtagsE = tagResE.map { case (k, v) => Json.obj("extractor_id" -> k, "values" -> Json.toJson(v)) }
    var tagResU = Map[String, MutableList[String]]()
    tags.filter(_.userId.isDefined).foreach(tag => {
      val ename = tag.userId.get
      if (tagResU.contains(ename)) {
        tagResU.get(ename).get += tag.name
      } else {
        tagResU += ((ename, MutableList(tag.name)))
      }
    })
    val jtagsU = tagResU.map { case (k, v) => Json.obj("userId" -> k, "values" -> Json.toJson(v)) }
    val jtags = jtagsE ++ jtagsU
    jtags
  }
  
  /**
   * Returns Previews extracted so far for a file
   * 
   */
  def extractPreviews(id: String) = {
    val previews = PreviewDAO.findByFileId(new ObjectId(id));
    /*
     * Initial implementation: Flat tree of {"extractor_id", "preview_id", "url", "contentType", ...} items.
     * 
    val jpreviews = filePreviews.map(p =>
      Json.obj("extractor_id" -> p.extractor_id, "preview_id" -> p.id.toString,
        "contentType" -> p.contentType, "url" -> api.routes.Previews.download(p.id.toString).toString))
    jpreviews
    * 
    */
    // Transform the preview list into a list of ["extractor_id", "values"] items,
    // where "values" are the preview properties, such as "preview_id", "url", and "contentType".
    var previewRes = Map[String, MutableList[JsValue]]()
    previews.filter(_.extractor_id.isDefined).foreach(p => {
      val ename = p.extractor_id.get
      val jitem = Json.obj("preview_id" -> p.id.toString,
        "contentType" -> p.contentType, "url" -> api.routes.Previews.download(p.id.toString).toString)
      if (previewRes.contains(ename)) {
        previewRes.get(ename).get += jitem
      } else {
        previewRes += ((ename, MutableList(jitem)))
      }
    })
    val jpreviews = previewRes.map { case (k, v) => Json.obj("extractor_id" -> k, "values" -> Json.toJson(v)) }
    jpreviews
  }
  
  /**
   * Returns Versus Descriptors from metadata field for a file
   * 
   */
  
  def extractVersusDescriptors(id:String): JsValue= {
    
    FileDAO.dao.collection.findOneByID(new ObjectId(id)) match {
      case Some(x) => {

        x.getAs[DBObject]("metadata") match {
          case None => {
            Logger.debug("No metadata field found: Adding meta data field")
            Json.obj("Message"->("No metadata for file"+id))
          }
          case Some(map) => {

            Logger.debug("metadata found ")

            val returnedMetadata = com.mongodb.util.JSON.serialize(x.getAs[DBObject]("metadata").get)
            Logger.debug("retmd: " + returnedMetadata)

            val retmd = toJson(returnedMetadata)
            //Logger.debug("Contains Keys versus descriptors: " + map.containsKey("versus_descriptors"))
            if(map.containsKey("versus_descriptors")){
             val listd = Json.parse(returnedMetadata) \ ("versus_descriptors")
             listd
            }
            else
            Json.obj(""->"")
           
          }
        }

      }
      case None => {
        Logger.error("Error getting file" + id)
        Json.obj("Error in File"->id)

      }
    }
  }
  
  /*convert list of JsObject to JsArray*/
def getJsonArray(list: List[JsObject]): JsArray = {
    list.foldLeft(JsArray())((acc, x) => acc ++ Json.arr(x))
  }


/**
 * For a given file id, checks for the status of all extractors processing that file.
 * REST endpoint  GET /api/extraction/:id/status 
 * input: file id
 * returns: a list of status of all extractors responsible for extractions on the file and the final status of extraction job
 * Async is going to deprecreate is subsequent version of Play Framework. So need to change SecuredAction class to be able use the Action.async
 */

def checkExtractorsStatus(id: String) = SecuredAction(parse.anyContent, authorization = WithPermission(Permission.ShowFile)) { implicit request =>
    Async {
      current.plugin[RabbitmqPlugin] match {

        case Some(plugin) => {
          files.getFile(id) match {
            case Some(file) => {
              
              var isActivity = "false"
              Extraction.findIfBeingProcessed(file.id) match {
                case false =>
                case true => isActivity = "true"
              }
              
              val l = Extraction.getExtractorList(file.id) map {
                elist => (elist._1, elist._2)
              }

              var blist = plugin.getBindings()

              for {
                rkeyResponse <- blist
              } yield {
                val rkeyjson = rkeyResponse.json
                val rkeyjsonlist = rkeyjson.as[List[JsObject]]
                val rkeylist = rkeyjsonlist.map {
                  rk =>
                    Logger.debug("Routing Key : " + rk \ "routing_key")
                    (rk \ "routing_key").toString
                }
                
                if (isActivity.equals("true")) {
                 // Logger.debug("***************Processing :l.size= " + l.size.toString)
                  l += "Status" -> "Processing"

                 // Logger.debug(l.toString)
                  Ok(toJson(l.toMap))
                } else {
                  val ct = file.contentType
                  val mt = ct.split("/")
                  for (m <- mt)
                    Logger.debug("m= " + m)
                
                  var flag = false
                  if (l.size == 0) {
                    
                    val rkl = rkeylist.toArray
                    
                    for (s <- rkl) {
                                            
                      var x = s.split("\\.")
                      //Logger.debug("after split x0=" + x(0) + "  x1=" + x(1))

                      if (x.length > 2) { // based on the routing key obtained from rabbitmq server
                        
                        if (x(2).equals(mt(0)) && !flag) {
                          Logger.debug("x(2)" + x(2) + "  mt(0): " + mt(0))
                          l += "Status" -> "Required Extractor is either busy or not currently running"
                          flag = true
                         
                        }
                      }
                    } //end of for rkl
                    
                    if (flag == false)
                      l += "Status" -> "No Extractor Available. Request is not Queued"

                  } else {
                    l += "Status" -> "Done"
                  }
                  Logger.debug("l.toString : " + l.toString)
                  Ok(toJson(l.toMap))
                }

              } //end of yield

            } //end of some file
            case None => {
              Future(Ok("no file"))
            }
          } //end of match file
        }

        case None => {
          Future(Ok("No Rabbitmq Service"))
        }
      }
    } //Async ends
  }

  /**
   * fetch the extracted metadata for the file
   * REST end-point: GET /api/extraction/:id/value
   * input: file id
   * Returns status of the extraction request and  metadata extracted so far   
   * 
   */

  def fetch(id: String) = SecuredAction(parse.anyContent, authorization = WithPermission(Permission.ShowFile)) { implicit request =>
    Async {
      current.plugin[RabbitmqPlugin] match {

        case Some(plugin) => {
          if (ObjectId.isValid(id)) {
            files.getFile(id) match {
              case Some(file) => {
                Logger.info("Getting extract info for file with id " + id)
                
                var isActivity = "false"
                Extraction.findIfBeingProcessed(file.id) match {
                  case false =>
                  case true => isActivity = "true"
                }
                val l = Extraction.getExtractorList(file.id) map {
                  elist => (elist._1, elist._2)
                }

                var blist = plugin.getBindings()

                for {
                  rkeyResponse <- blist
                } yield {
                  val rkeyjson = rkeyResponse.json
                  val rkeyjsonlist = rkeyjson.as[List[JsObject]]
                  val rkeylist = rkeyjsonlist.map {
                    rk =>
                      Logger.debug("Routing Key : " + rk \ "routing_key")
                      (rk \ "routing_key").toString
                  }
                  var status = ""
                  if (isActivity.equals("true")) {
                     status = "Processing"

                  } else {
                    val ct = file.contentType
                    val mt = ct.split("/")
                    for (m <- mt)
                      Logger.debug("m= " + m)

                    var flag = false
                    if (l.size == 0) {
                      Logger.debug("Inside If")
                      val rkl = rkeylist.toArray
                      
                      for (s <- rkl) {
                        var x = s.split("\\.")
                        if (x.length > 2) {
                          if (x(2).equals(mt(0)) && !flag) {
                            Logger.debug("x(2)" + x(2) + "  mt(0): " + mt(0))
                            status = "Required Extractor is either busy or is not currently running. Try after some time."
                            flag = true
                             
                          }
                        }
                      }
                      
                      if (flag == false)
                        status = "No Extractor Available. Request is not queued."

                    } else {
                      status = "Done"
                    }
                    
                  } //end of outer else
                  
                  val jtags = extractTags(file)
                  val jpreviews = extractPreviews(id)
                  val vdescriptors = extractVersusDescriptors(id)
                  Logger.debug("jtags: " + jtags.toString)
                  Logger.debug("jpreviews: " + jpreviews.toString)
                  
                  Ok(Json.obj("file_id" -> id, "filename" -> file.filename, "Status" -> status, "tags" -> jtags, "previews" -> jpreviews, "versus descriptors" -> vdescriptors))
                } //end of yield

              } //end of some file
              case None => {
                val error_str = "The file with id " + id + " is not found."
                Logger.error(error_str)
                Future(NotFound(toJson(error_str)))
              }
            } //end of match file
          } else {
            val error_str = "The given id " + id + " is not a valid ObjectId."
            Logger.error(error_str)
            Future(BadRequest(Json.toJson(error_str)))
          }

        }

        case None => {
          Future(Ok("No Rabbitmq Service"))
        }
      }
    } //Async 
  }
  
  
 /**
  * REST endpoint: GET  api/files/:id/extract 
  * Returns metadata extracted so far for a file with id
  * 
  */
    
  def extract(id: String) = SecuredAction(parse.anyContent, authorization = WithPermission(Permission.ShowFile)) { implicit request =>
    Logger.info("Getting extract info for file with id " + id)
    if (ObjectId.isValid(id)) {
     files.getFile(id) match {
        case Some(file) =>
          val jtags = extractTags(file)
          val jpreviews = extractPreviews(id)
          val vdescriptors=extractVersusDescriptors(id)
          Logger.debug("jtags: " + jtags.toString)
          Logger.debug("jpreviews: " + jpreviews.toString)
          Ok(Json.obj("file_id" -> id, "filename" -> file.filename, "tags" -> jtags, "previews" -> jpreviews,"versus descriptors"->vdescriptors))
        case None => {
          val error_str = "The file with id " + id + " is not found." 
          Logger.error(error_str)
          NotFound(toJson(error_str))
        }
      }
    } else {
      val error_str ="The given id " + id + " is not a valid ObjectId." 
      Logger.error(error_str)
      BadRequest(toJson(error_str))
    }
  }
  
  
  // ---------- Extract (BD-38) related code ends ------------------

  def comment(id: String) = SecuredAction(authorization=WithPermission(Permission.CreateComments))  { implicit request =>
	  request.user match {
	    case Some(identity) => {
		    request.body.\("text").asOpt[String] match {
			    case Some(text) => {
			        val comment = new Comment(identity, text, file_id=Some(id))
			        Comment.save(comment)
			        index(id)
			        Ok(comment.id.toString)
			    }
			    case None => {
			    	Logger.error("no text specified.")
			    	BadRequest(toJson("no text specified."))
			    }
		    }
	    }
	    case None =>
	      Logger.error(("No user identity found in the request, request body: " + request.body))
	      BadRequest(toJson("No user identity found in the request, request body: " + request.body))
	  }
  }
	
	
  /**
   * Return whether a file is currently being processed.
   */
  def isBeingProcessed(id: String) = SecuredAction(parse.anyContent, authorization=WithPermission(Permission.ShowFile)) { request =>
  	files.getFile(id) match {
  	  case Some(file) => { 	    
  		  var isActivity = "false"
		  Extraction.findIfBeingProcessed(file.id) match{
			  case false => 
			  case true => isActivity = "true"
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
    files.getFile(id)  match {
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
  
  
  def getTechnicalMetadataJSON(id: String) = SecuredAction(parse.anyContent, authorization=WithPermission(Permission.ShowFilesMetadata)) { request =>
    files.getFile(id)  match {
      case Some(file) => {
        Ok(FileDAO.getTechnicalMetadataJSON(id))
      }
      case None => {Logger.error("Error finding file" + id); InternalServerError}      
    }
  }
  
  
  def removeFile(id: String) = SecuredAction(parse.anyContent, authorization=WithPermission(Permission.DeleteFiles)) { request =>
    files.getFile(id)  match {
      case Some(file) => {
        FileDAO.removeFile(id)
        Ok(toJson(Map("status"->"success")))
      }
      case None => Ok(toJson(Map("status"->"success")))
    }
  }
  
/**
   * List datasets satisfying a user metadata search tree.
   */
  def searchFilesUserMetadata = SecuredAction(authorization=WithPermission(Permission.SearchFiles)) { request =>
      Logger.debug("Searching files' user metadata for search tree.")
      
      var searchJSON = Json.stringify(request.body)
      Logger.debug("thejsson: "+searchJSON)
      var searchTree = JsonUtil.parseJSON(searchJSON).asInstanceOf[java.util.LinkedHashMap[String, Any]]
      
      var searchQuery = FileDAO.searchUserMetadataFormulateQuery(searchTree)
      
      //searchQuery = searchQuery.reverse

      Logger.debug("Search completed. Returning files list.")

      val list = for (file <- searchQuery) yield jsonFileWithThumbnail(file)
      Logger.debug("thelist: " + toJson(list))
      Ok(toJson(list))
    }   

  
  /**
   * List datasets satisfying a general metadata search tree.
   */
  def searchFilesGeneralMetadata = SecuredAction(authorization=WithPermission(Permission.SearchFiles)) { request =>
      Logger.debug("Searching files' metadata for search tree.")
      
      var searchJSON = Json.stringify(request.body)
      Logger.debug("thejsson: "+searchJSON)
      var searchTree = JsonUtil.parseJSON(searchJSON).asInstanceOf[java.util.LinkedHashMap[String, Any]]
      
      var searchQuery = FileDAO.searchAllMetadataFormulateQuery(searchTree)
      
      //searchQuery = searchQuery.reverse

      Logger.debug("Search completed. Returning files list.")

      val list = for (file <- searchQuery) yield jsonFileWithThumbnail(file)
      Logger.debug("thelist: " + toJson(list))
      Ok(toJson(list))
    }
  
  
  def index(id: String) {
    files.getFile(id) match {
      case Some(file) => {
        var tagListBuffer = new ListBuffer[String]()
        
        for (tag <- file.tags){
          tagListBuffer += tag.name
        }          
        
        val tagsJson = new JSONArray(tagListBuffer.toList)

        Logger.debug("tagStr=" + tagsJson);

        val comments = for(comment <- Comment.findCommentsByFileId(id)) yield {
          comment.text
        }
        val commentJson = new JSONArray(comments)

        Logger.debug("commentStr=" + commentJson.toString())
        
        val usrMd = FileDAO.getUserMetadataJSON(id)
        Logger.debug("usrmd=" + usrMd)
        
        val techMd = FileDAO.getTechnicalMetadataJSON(id)
        Logger.debug("techmd=" + techMd)
        
        val xmlMd = FileDAO.getXMLMetadataJSON(id)
	    Logger.debug("xmlmd=" + xmlMd)
        
        var fileDsId = ""
        var fileDsName = ""
          
        for(dataset <- Dataset.findByFileId(file.id)){
          fileDsId = fileDsId + dataset.id.toString + "  "
          fileDsName = fileDsName + dataset.name + "  "
        }  

        current.plugin[ElasticsearchPlugin].foreach {
          _.index("data", "file", id,
            List(("filename", file.filename), ("contentType", file.contentType),("datasetId",fileDsId),("datasetName",fileDsName), ("tag", tagsJson.toString), ("comments", commentJson.toString), ("usermetadata", usrMd), ("technicalmetadata", techMd), ("xmlmetadata", xmlMd)))
        }
      }
      case None => Logger.error("File not found: " + id)
    }
  }
	
}
