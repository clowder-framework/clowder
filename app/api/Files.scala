package api
import java.io.FileInputStream


import java.io.BufferedWriter
import java.io.FileWriter

import java.text.SimpleDateFormat
import java.util.Date

import org.bson.types.ObjectId

import com.mongodb.casbah.Imports._

import models._
import play.api.Logger
import play.api.Play.current
import play.api.libs.concurrent.Execution.Implicits._
import play.api.libs.iteratee.Enumerator
import play.api.libs.json._
import play.api.libs.json.JsValue
import play.api.libs.json.Json._

import javax.inject.Inject

import scala.collection.mutable.ListBuffer

import org.json.JSONObject

import Transformation.LidoToCidocConvertion

import jsonutils.JsonUtil

import services._
import fileutils.FilesUtils

import play.api.libs.json.JsString
import services.DumpOfFile
import play.api.mvc.ResponseHeader
import models.Preview
import play.api.mvc.SimpleResult
import models.File
import play.api.libs.json.JsObject
import play.api.Play.configuration
import com.wordnik.swagger.annotations.{ApiOperation, Api}

import services.ExtractorMessage
import scala.util.parsing.json.JSONArray

import controllers.Previewers

import java.io.BufferedInputStream
import javax.imageio.ImageIO

import scala.concurrent.Future
 
import scala.util.control._
import controllers.Utils


/**
 * Json API for files.
 *
 * @author Luigi Marini
 *
 */
@Api(value = "/files", listingPath = "/api-docs.json/files", description = "A file is the raw bytes plus metadata.")
class Files @Inject()(
  files: FileService,
  datasets: DatasetService,
  queries: MultimediaQueryService,
  tags: TagService,
  comments: CommentService,
  extractions: ExtractionService,
  dtsrequests:ExtractionRequestsService,
  previews: PreviewService,
  threeD: ThreeDService,
  sqarql: RdfSPARQLService,
  thumbnails: ThumbnailService) extends ApiController {


  @ApiOperation(value = "Retrieve physical file object metadata",
      notes = "Get metadata of the file object (not the resource it describes) as JSON. For example, size of file, date created, content type, filename.",
      responseClass = "None", httpMethod = "GET")
  def get(id: UUID) = SecuredAction(parse.anyContent, authorization = WithPermission(Permission.ShowFile)) {
    implicit request =>
      Logger.info("GET file with id " + id)
      files.get(id) match {
        case Some(file) => Ok(jsonFile(file))
        case None => {
          Logger.error("Error getting file" + id);
          InternalServerError
        }
      }

  }
  /**
   * List all files.
   */
  @ApiOperation(value = "List all files", notes = "Returns list of files and descriptions.", responseClass = "None", httpMethod = "GET")
  def list = SecuredAction(parse.anyContent, authorization = WithPermission(Permission.ListFiles)) {
    request =>
      val list = for (f <- files.listFiles()) yield jsonFile(f)

      Ok(toJson(list))

  }

  def downloadByDatasetAndFilename(datasetId: UUID, filename: String, preview_id: UUID) =
    SecuredAction(parse.anyContent, authorization = WithPermission(Permission.DownloadFiles)) {
      request =>
        datasets.getFileId(datasetId, filename) match {
          case Some(id) => Redirect(routes.Files.download(id))
          case None => Logger.error("Error getting dataset" + datasetId); InternalServerError
        }
    }

  /**
   * Download file using http://en.wikipedia.org/wiki/Chunked_transfer_encoding
   */
  @ApiOperation(value = "Download file",
      notes = "Can use Chunked transfer encoding if the HTTP header RANGE is set.",
      responseClass = "None", httpMethod = "GET")
  def download(id: UUID) = SecuredAction(parse.anyContent) { request =>
    files.get(id) match {
      case Some(file) => {
        //Check the license type before doing anything.
        if (file.checkLicenseForDownload(request.user)) {
          files.getBytes(id) match {
            case Some((inputStream, filename, contentType, contentLength)) => {
              request.headers.get(RANGE) match {
                // user requested a range of an image
                case Some(value) => {
                  val range: (Long, Long) = value.substring("bytes=".length).split("-") match {
                    case x if x.length == 1 => (x.head.toLong, contentLength - 1)
                    case x => (x(0).toLong, x(1).toLong)
                  }

                  range match {
                    case (start, end) =>
                      inputStream.skip(start)
                      SimpleResult(
                        header = ResponseHeader(PARTIAL_CONTENT,
                          Map(
                            CONNECTION -> "keep-alive",
                            ACCEPT_RANGES -> "bytes",
                            CONTENT_RANGE -> "bytes %d-%d/%d".format(start, end, contentLength),
                            CONTENT_LENGTH -> (end - start + 1).toString,
                            CONTENT_TYPE -> contentType
                          )
                        ),
                        body = Enumerator.fromStream(inputStream)
                      )
                  }
                }
                // return full image
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
        else {
          //Case where the checkLicenseForDownload fails
          Logger.error("The file is not able to be downloaded")
          BadRequest("The license for this file does not allow it to be downloaded.")
        }
      }
      case None => {
        //Case where the file could not be found
        Logger.info(s"Error getting the file with id $id.")
        BadRequest("Invalid file ID")
      }
    }
  }

  /**
   *
   * Download query used by Versus
   *
   */
  def downloadquery(id: UUID) =
    SecuredAction(parse.anyContent, authorization = WithPermission(Permission.DownloadFiles)) {
      request =>
        queries.get(id) match {
          case Some((inputStream, filename, contentType, contentLength)) => {
            request.headers.get(RANGE) match {
              case Some(value) => {
                val range: (Long, Long) = value.substring("bytes=".length).split("-") match {
                  case x if x.length == 1 => (x.head.toLong, contentLength - 1)
                  case x => (x(0).toLong, x(1).toLong)
                }
                range match {
                  case (start, end) =>
                    inputStream.skip(start)
                    SimpleResult(
                      header = ResponseHeader(PARTIAL_CONTENT,
                        Map(
                          CONNECTION -> "keep-alive",
                          ACCEPT_RANGES -> "bytes",
                          CONTENT_RANGE -> "bytes %d-%d/%d".format(start, end, contentLength),
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
  @ApiOperation(value = "Add technical metadata to file",
      notes = "Metadata in attached JSON object will describe the file's described resource, not the file object itself.",
      responseClass = "None", httpMethod = "POST")
  def addMetadata(id: UUID) =
    SecuredAction(authorization = WithPermission(Permission.AddFilesMetadata)) {
      request =>
        Logger.debug(s"Adding metadata to file $id")
        val doc = com.mongodb.util.JSON.parse(Json.stringify(request.body)).asInstanceOf[DBObject]
        files.get(id) match {
          case Some(x) => {
            files.addMetadata(id, request.body)
            index(id)
          }
          case None => Logger.error(s"Error getting file $id"); NotFound
        }

        Logger.debug(s"Updating previews.files $id with $doc")
        Ok(toJson("success"))
    }


  /**
   * Add Versus metadata to file: use by Versus Extractor
   * REST enpoint:POST api/files/:id/versus_metadata
   */
  def addVersusMetadata(id: UUID) =
    SecuredAction(authorization = WithPermission(Permission.AddFilesMetadata)) { request =>

     Logger.debug("INSIDE ADDVersusMetadata=: "+id.toString )
      files.get(id) match {
        case Some(file) => {
          Logger.debug("******ADD Versus Metadata:*****")
          val list = request.body \ ("versus_descriptors")
          //files.addVersusMetadata(id, list)
          files.addVersusMetadata(id, request.body)
          Ok("Added Versus Descriptor")
        }
        case None => {
          Logger.error("Error in getting file " + id)
          NotFound
        }
      }

    }
 
  /**
   * Upload file using multipart form enconding.
   */
  @ApiOperation(value = "Upload file",
      notes = "Upload the attached file using multipart form enconding. Returns file id as JSON object. ID can be used to work on the file using the API. Uploaded file can be an XML metadata file.",
      responseClass = "None", httpMethod = "POST")
  def upload(showPreviews: String = "DatasetLevel", originalZipFile: String = "") = SecuredAction(parse.multipartFormData, authorization = WithPermission(Permission.CreateFiles)) {
    implicit request =>
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
	        var realUser = user
	          if(!originalZipFile.equals("")){
	             files.get(new UUID(originalZipFile)) match{
	               case Some(originalFile) => {
	                 realUser = originalFile.author
	               }
	               case None => {}
	             }
	         }

	        var realUserName = realUser.fullName

	        val file = files.save(new FileInputStream(f.ref.file), nameOfFile, f.contentType, realUser, showPreviews)
	        val uploadedFile = f
	        file match {
	          case Some(f) => {
	            	            
	            val id = f.id
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
	            	if(fileType.equals("imageset/ptmimages-zipped") || fileType.equals("imageset/ptmimages+zipped") ){
					        	  var thirdSeparatorIndex = nameOfFile.indexOf("__")
					              if(thirdSeparatorIndex >= 0){
					                var firstSeparatorIndex = nameOfFile.indexOf("_")
					                var secondSeparatorIndex = nameOfFile.indexOf("_", firstSeparatorIndex+1)
					            	flags = flags + "+numberofIterations_" +  nameOfFile.substring(0,firstSeparatorIndex) + "+heightFactor_" + nameOfFile.substring(firstSeparatorIndex+1,secondSeparatorIndex)+ "+ptm3dDetail_" + nameOfFile.substring(secondSeparatorIndex+1,thirdSeparatorIndex)
					            	nameOfFile = nameOfFile.substring(thirdSeparatorIndex+2)
					            	files.renameFile(f.id, nameOfFile)
					              }
					        	  files.setContentType(f.id, fileType)
					          }
	            }
	            else if(nameOfFile.toLowerCase().endsWith(".mov")){
							  fileType = "ambiguous/mov";
						  }
	            
	            current.plugin[FileDumpService].foreach{_.dump(DumpOfFile(uploadedFile.ref.file, f.id.toString, nameOfFile))}


                  val key = "unknown." + "file." + fileType.replace(".", "_").replace("/", ".")
                  val host = Utils.baseUrl(request) + request.path.replaceAll("api/files$", "")


                  /*---- Insert DTS Request to database---*/  

                  val clientIP=request.remoteAddress
                  val serverIP= request.host
                  dtsrequests.insertRequest(serverIP,clientIP, f.filename, id, fileType, f.length,f.uploadDate)

                  /*---------------------------------------*/ 
	            
                  // index the file using Versus
                  current.plugin[VersusPlugin].foreach{ _.index(f.id.toString,fileType) }
	            
	            
                  current.plugin[RabbitmqPlugin].foreach {
                    // TODO replace null with None
                    _.extract(ExtractorMessage(id, id, host, key, Map.empty, f.length.toString, null, flags))
                  }
                  val dateFormat = new SimpleDateFormat("dd/MM/yyyy") 
                  //for metadata files
                  if (fileType.equals("application/xml") || fileType.equals("text/xml")) {
                    val xmlToJSON = FilesUtils.readXMLgetJSON(uploadedFile.ref.file)
                    files.addXMLMetadata(id, xmlToJSON)

                    Logger.debug("xmlmd=" + xmlToJSON)

                    current.plugin[ElasticsearchPlugin].foreach {
                      _.index("data", "file", id, List(("filename", nameOfFile), ("contentType", f.contentType), ("author", realUserName), ("uploadDate", dateFormat.format(new Date())),("xmlmetadata", xmlToJSON)))
                    }

                    //add file to RDF triple store if triple store is used
                    configuration.getString("userdfSPARQLStore").getOrElse("no") match {

                      case "yes" => sqarql.addFileToGraph(f.id)
                      case _ => {}
                    }
	            }
	            else{
		            current.plugin[ElasticsearchPlugin].foreach{
		              _.index("data", "file", id, List(("filename",nameOfFile), ("contentType", f.contentType), ("author", realUserName), ("uploadDate", dateFormat.format(new Date()))))
		            }
	            }
	            

	            Ok(toJson(Map("id"->id.stringify)))
	            current.plugin[AdminsNotifierPlugin].foreach{
                _.sendAdminsNotification(Utils.baseUrl(request), "File","added",id.stringify, nameOfFile)}
	            Ok(toJson(Map("id"->id.stringify)))
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
  @ApiOperation(value = "(Re)send preprocessing job for file",
      notes = "Force Medici to (re)send preprocessing job for selected file, processing the file as a file of the selected MIME type. Returns file id on success. In the requested file type, replace / with __ (two underscores).",
      responseClass = "None", httpMethod = "POST")
  def sendJob(file_id: UUID, fileType: String) = SecuredAction(parse.anyContent, authorization = WithPermission(Permission.CreateFiles)) {
    implicit request =>
      files.get(file_id) match {
        case Some(theFile) => {
          var nameOfFile = theFile.filename
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

          val showPreviews = theFile.showPreviews

          Logger.debug("(Re)sending job for file " + nameOfFile)

          val id = theFile.id
          if (showPreviews.equals("None"))
            flags = flags + "+nopreviews"

          val key = "unknown." + "file." + fileType.replace("__", ".")
          val host = Utils.baseUrl(request) + request.path.replaceAll("api/files/sendJob/[A-Za-z0-9_]*/.*$", "")

          // TODO replace null with None
          current.plugin[RabbitmqPlugin].foreach {
            _.extract(ExtractorMessage(id, id, host, key, Map.empty, theFile.length.toString, null, flags))
          }

          Ok(toJson(Map("id" -> id.stringify)))

        }
        case None => {
          BadRequest(toJson("File not found."))
        }
      }
  }


  /**
   * Upload a file to a specific dataset
   */
 @ApiOperation(value = "Upload a file to a specific dataset",
      notes = "Uploads the file, then links it with the dataset. Returns file id as JSON object. ID can be used to work on the file using the API. Uploaded file can be an XML metadata file to be added to the dataset.",
      responseClass = "None", httpMethod = "POST")
  def uploadToDataset(dataset_id: UUID, showPreviews: String="DatasetLevel", originalZipFile: String = "") = SecuredAction(parse.multipartFormData, authorization=WithPermission(Permission.CreateFiles)) { implicit request =>
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
          var realUser = user
          if(!originalZipFile.equals("")){
             files.get(new UUID(originalZipFile)) match{
               case Some(originalFile) => {
                 realUser = originalFile.author
               }
               case None => {}
             }
         }          

          var realUserName = realUser.fullName

          val file = files.save(new FileInputStream(f.ref.file), nameOfFile, f.contentType, realUser, showPreviews)
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
	        	  if(fileType.equals("imageset/ptmimages-zipped") || fileType.equals("imageset/ptmimages+zipped") ){
					        	  var thirdSeparatorIndex = nameOfFile.indexOf("__")
					              if(thirdSeparatorIndex >= 0){
					                var firstSeparatorIndex = nameOfFile.indexOf("_")
					                var secondSeparatorIndex = nameOfFile.indexOf("_", firstSeparatorIndex+1)
					            	flags = flags + "+numberofIterations_" +  nameOfFile.substring(0,firstSeparatorIndex) + "+heightFactor_" + nameOfFile.substring(firstSeparatorIndex+1,secondSeparatorIndex)+ "+ptm3dDetail_" + nameOfFile.substring(secondSeparatorIndex+1,thirdSeparatorIndex)
					            	nameOfFile = nameOfFile.substring(thirdSeparatorIndex+2)
					            	files.renameFile(f.id, nameOfFile)
					              }
					        	  files.setContentType(f.id, fileType)
					          }
	          }
	          else if(nameOfFile.toLowerCase().endsWith(".mov")){
							  fileType = "ambiguous/mov";
						  }
	              
              current.plugin[FileDumpService].foreach{_.dump(DumpOfFile(uploadedFile.ref.file, f.id.toString, nameOfFile))}
              
	          // TODO RK need to replace unknown with the server name
	          val key = "unknown." + "file." + fileType.replace(".", "_").replace("/", ".")
	          val host = Utils.baseUrl(request) + request.path.replaceAll("api/uploadToDataset/[A-Za-z0-9_]*$", "")
	          /*----- Insert DTS Requests  -------*/
	          val clientIP = request.remoteAddress
	          val serverIP = request.host
	          dtsrequests.insertRequest(serverIP, clientIP, f.filename, f.id, fileType, f.length, f.uploadDate)
			 /*-------------------------*/ 
                      
			  // index the file using Versus
			  current.plugin[VersusPlugin].foreach{ _.index(f.id.toString,fileType) }    
	          
	          current.plugin[RabbitmqPlugin].foreach { _.extract(ExtractorMessage(new UUID(id), new UUID(id), host, key, Map.empty, f.length.toString, dataset_id, flags)) }
	          
	          val dateFormat = new SimpleDateFormat("dd/MM/yyyy")
	          
	          //for metadata files
              if(fileType.equals("application/xml") || fileType.equals("text/xml")){
            	  		  val xmlToJSON = FilesUtils.readXMLgetJSON(uploadedFile.ref.file)
            	  		  files.addXMLMetadata(new UUID(id), xmlToJSON)

            			  Logger.debug("xmlmd=" + xmlToJSON)

                        current.plugin[ElasticsearchPlugin].foreach {
            	  		     
            		  		_.index("data", "file", new UUID(id), List(("filename",f.filename), ("contentType", f.contentType), ("author", realUserName), ("uploadDate", dateFormat.format(new Date())),("datasetId",dataset.id.toString),("datasetName",dataset.name), ("xmlmetadata", xmlToJSON)))
            	  		  
                         
                        }
                      }
                      else {
                        current.plugin[ElasticsearchPlugin].foreach {
                          _.index("data", "file", new UUID(id), List(("filename", f.filename), ("contentType", f.contentType), ("author", realUserName), ("uploadDate", dateFormat.format(new Date())),("datasetId", dataset.id.toString), ("datasetName", dataset.name)))
                        }
                      }

               // add file to dataset   
              // TODO create a service instead of calling salat directly
              val theFile = files.get(f.id)
              if(theFile.isEmpty){
                 Logger.error("Could not retrieve file that was just saved.")
                 InternalServerError("Error uploading file")                
              }
              else{
            	  datasets.addFile(dataset.id, theFile.get)

	              datasets.index(dataset_id)
			      	

            	  // TODO RK need to replace unknown with the server name and dataset type
            	  val dtkey = "unknown." + "dataset." + "unknown"

              	  current.plugin[RabbitmqPlugin].foreach { _.extract(ExtractorMessage(dataset_id, dataset_id, host, dtkey, Map.empty, f.length.toString, dataset_id, "")) }

            	  Logger.info("Uploading Completed")
              
	              //add file to RDF triple store if triple store is used
	              if(fileType.equals("application/xml") || fileType.equals("text/xml")){
			             configuration.getString("userdfSPARQLStore").getOrElse("no") match{      
				             case "yes" => {
				               sqarql.addFileToGraph(f.id)
				               sqarql.linkFileToDataset(f.id,dataset_id)
				             }
				             case _ => {}
			             }
	              }

              //sending success message
              Ok(toJson(Map("id" -> id)))
              current.plugin[AdminsNotifierPlugin].foreach{
                _.sendAdminsNotification(Utils.baseUrl(request), "File","added",id, nameOfFile)}
              Ok(toJson(Map("id" -> id)))
             }
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
  def uploadIntermediate(originalIdAndFlags: String) = SecuredAction(parse.multipartFormData, authorization = WithPermission(Permission.CreateFiles)) {
    implicit request =>
      request.user match {
        case Some(user) => {
          request.body.file("File").map {
            f =>
              var originalId = originalIdAndFlags
              var flags = ""
              if (originalIdAndFlags.indexOf("+") != -1) {
                originalId = originalIdAndFlags.substring(0, originalIdAndFlags.indexOf("+"));
                flags = originalIdAndFlags.substring(originalIdAndFlags.indexOf("+"));
              }

              Logger.debug("Uploading intermediate file " + f.filename + " associated with original file with id " + originalId)
              // store file
              val file = files.save(new FileInputStream(f.ref.file), f.filename, f.contentType, user)
              val uploadedFile = f
              file match {
                case Some(f) => {
                  files.setIntermediate(f.id)
                  var fileType = f.contentType
                  if (fileType.contains("/zip") || fileType.contains("/x-zip") || f.filename.toLowerCase().endsWith(".zip")) {
                    fileType = FilesUtils.getMainFileTypeOfZipFile(uploadedFile.ref.file, f.filename, "file")
                    if (fileType.startsWith("ERROR: ")) {
                      Logger.error(fileType.substring(7))
                      InternalServerError(fileType.substring(7))
                    }
                  }
                  else if (f.filename.toLowerCase().endsWith(".mov")) {
                    fileType = "ambiguous/mov";
                  }

                  val key = "unknown." + "file." + fileType.replace(".", "_").replace("/", ".")
                  val host = Utils.baseUrl(request) + request.path.replaceAll("api/files/uploadIntermediate/[A-Za-z0-9_+]*$", "")
                  val id = f.id
                  // TODO replace null with None
                   // index the file using Versus
                  current.plugin[VersusPlugin].foreach{ _.index(f.id.toString,fileType) }
                  
                  current.plugin[RabbitmqPlugin].foreach {
                    _.extract(ExtractorMessage(UUID(originalId), id, host, key, Map.empty, f.length.toString, null, flags))
                  }
                  Ok(toJson(Map("id" -> id.stringify)))
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
  def uploadPreview(file_id: UUID) = SecuredAction(parse.multipartFormData, authorization = WithPermission(Permission.CreateFiles)) {
    implicit request =>
      request.body.file("File").map {
        f =>
          Logger.debug("Uploading file " + f.filename)
          // store file
          val id = previews.save(new FileInputStream(f.ref.file), f.filename, f.contentType)
          Ok(toJson(Map("id" -> id)))
      }.getOrElse {
        BadRequest(toJson("File not attached."))
      }
  }

  /**
   * Add preview to file.
   */
  @ApiOperation(value = "Attach existing preview to file",
      notes = "",
      responseClass = "None", httpMethod = "POST")
  def attachPreview(file_id: UUID, preview_id: UUID) = SecuredAction(authorization = WithPermission(Permission.CreateFiles)) {
    request =>
    // Use the "extractor_id" field contained in the POST data.  Use "Other" if absent.
      val eid = (request.body \ "extractor_id").asOpt[String]
      val extractor_id = if (eid.isDefined) {
        eid
      } else {
        Logger.debug("api.Files.attachPreview(): No \"extractor_id\" specified in request, set it to None.  request.body: " + request.body.toString)
        Some("Other")
      }
      request.body match {
        case JsObject(fields) => {
          files.get(file_id) match {
            case Some(file) => {
              previews.get(preview_id) match {
                case Some(preview) =>
                  // "extractor_id" is stored at the top level of "Preview".  Remove it from the "metadata" field to avoid dup.
                  // TODO replace null with None
                  previews.attachToFile(preview_id, file_id, extractor_id, request.body)
                  Ok(toJson(Map("status" -> "success")))
                case None => BadRequest(toJson("Preview not found"))
              }
            }
            //If file to be previewed is not found, just delete the preview
            case None => {
              previews.get(preview_id) match {
                case Some(preview) =>
                  Logger.debug("File not found. Deleting previews.files " + preview_id)
                  previews.removePreview(preview)
                  BadRequest(toJson("File not found. Preview deleted."))
                case None => BadRequest(toJson("Preview not found"))
              }
            }
          }
        }
        case _ => Ok("received something else: " + request.body + '\n')
      }
  }

  @ApiOperation(value = "Get the user-generated metadata of the selected file in an RDF file",
	      notes = "",
	      responseClass = "None", httpMethod = "GET")
  def getRDFUserMetadata(id: UUID, mappingNumber: String = "1") = SecuredAction(parse.anyContent, authorization = WithPermission(Permission.ShowFilesMetadata)) {
    implicit request =>
      configuration.getString("rdfexporter") match {
        case Some("on") => {
          files.get(id) match {
            case Some(file) => {
              val theJSON = files.getUserMetadataJSON(id)
              val fileSep = System.getProperty("file.separator")
              val tmpDir = System.getProperty("java.io.tmpdir")
              var resultDir = tmpDir + fileSep + "medici__rdfdumptemporaryfiles" + fileSep + new ObjectId().toString
              new java.io.File(resultDir).mkdirs()

              if (!theJSON.replaceAll(" ", "").equals("{}")) {
                val xmlFile = jsonToXML(theJSON)
                new LidoToCidocConvertion(configuration.getString("filesxmltordfmapping.dir_" + mappingNumber).getOrElse(""), xmlFile.getAbsolutePath(), resultDir)
                xmlFile.delete()
              }
              else {
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
        case _ => {
          Ok("RDF export features not enabled")
        }
      }
  }

  def jsonToXML(theJSON: String): java.io.File = {

    val jsonObject = new JSONObject(theJSON)
    var xml = org.json.XML.toString(jsonObject)

    //Remove spaces from XML tags
    var currStart = xml.indexOf("<")
    var currEnd = -1
    var xmlNoSpaces = ""
    while (currStart != -1) {
      xmlNoSpaces = xmlNoSpaces + xml.substring(currEnd + 1, currStart)
      currEnd = xml.indexOf(">", currStart + 1)
      xmlNoSpaces = xmlNoSpaces + xml.substring(currStart, currEnd + 1).replaceAll(" ", "_")
      currStart = xml.indexOf("<", currEnd + 1)
    }
    xmlNoSpaces = xmlNoSpaces + xml.substring(currEnd + 1)

    val xmlFile = java.io.File.createTempFile("xml", ".xml")
    val fileWriter = new BufferedWriter(new FileWriter(xmlFile))
    fileWriter.write(xmlNoSpaces)
    fileWriter.close()
    
    return xmlFile
  }
  
  @ApiOperation(value = "Get URLs of file's RDF metadata exports.",
	      notes = "URLs of metadata files exported from XML (if the file was an XML metadata file) as well as the URL used to export the file's user-generated metadata as RDF.",
	      responseClass = "None", httpMethod = "GET")
  def getRDFURLsForFile(id: UUID) = SecuredAction(parse.anyContent, authorization = WithPermission(Permission.ShowFilesMetadata)) {
    request =>
      configuration.getString("rdfexporter") match {
        case Some("on") => {
          files.get(id) match {
            case Some(file) => {

              //RDF from XML of the file itself (for XML metadata-only files)
              val previewsList = previews.findByFileId(id)
              var rdfPreviewList = List.empty[models.Preview]
              for (currPreview <- previewsList) {
                if (currPreview.contentType.equals("application/rdf+xml")) {
                  rdfPreviewList = rdfPreviewList :+ currPreview
                }
              }
              var hostString = Utils.baseUrl(request) + request.path.replaceAll("files/getRDFURLsForFile/[A-Za-z0-9_]*$", "previews/")
              var list = for (currPreview <- rdfPreviewList) yield Json.toJson(hostString + currPreview.id.toString)

              //RDF from export of file community-generated metadata to RDF
              var connectionChars = ""
              if (hostString.contains("?")) {
                connectionChars = "&mappingNum="
              }
              else {
                connectionChars = "?mappingNum="
              }
              hostString = Utils.baseUrl(request) + request.path.replaceAll("/getRDFURLsForFile/", "/rdfUserMetadata/") + connectionChars
              val mappingsQuantity = Integer.parseInt(configuration.getString("filesxmltordfmapping.dircount").getOrElse("1"))
              for (i <- 1 to mappingsQuantity) {
                var currHostString = hostString + i
                list = list :+ Json.toJson(currHostString)
              }

              val listJson = toJson(list.toList)

              Ok(listJson)
            }
            case None => {
              Logger.error("Error getting file" + id);
              InternalServerError
            }
          }
        }
        case _ => {
          Ok("RDF export features not enabled")
        }
      }
  }
  
    @ApiOperation(value = "Add user-generated metadata to file",
	      notes = "Metadata in attached JSON object will describe the file's described resource, not the file object itself.",
	      responseClass = "None", httpMethod = "POST")
    def addUserMetadata(id: UUID) = SecuredAction(authorization = WithPermission(Permission.AddFilesMetadata)) {
        implicit request =>
          Logger.debug("Adding user metadata to file " + id)
          val theJSON = Json.stringify(request.body)
          files.addUserMetadata(id, theJSON)
          files.index(id)
          configuration.getString("userdfSPARQLStore").getOrElse("no") match {
            case "yes" => {
              files.setUserMetadataWasModified(id, true)
            }
            case _ => {}
          }

          Ok(toJson(Map("status" -> "success")))
      }

  def jsonFile(file: File): JsValue = {
    toJson(Map("id" -> file.id.toString, "filename" -> file.filename, "content-type" -> file.contentType, "date-created" -> file.uploadDate.toString(), "size" -> file.length.toString))
  }

  def jsonFileWithThumbnail(file: File): JsValue = {
    var fileThumbnail = "None"
    if (!file.thumbnail_id.isEmpty)
      fileThumbnail = file.thumbnail_id.toString().substring(5, file.thumbnail_id.toString().length - 1)

    toJson(Map("id" -> file.id.toString, "filename" -> file.filename, "contentType" -> file.contentType, "dateCreated" -> file.uploadDate.toString(), "thumbnail" -> fileThumbnail))
  }

  def toDBObject(fields: Seq[(String, JsValue)]): DBObject = {
    fields.map(field =>
      field match {
        // TODO handle jsarray
        //          case (key, JsArray(value: Seq[JsValue])) => MongoDBObject(key -> getValueForSeq(value))
        case (key, jsObject: JsObject) => MongoDBObject(key -> toDBObject(jsObject.fields))
        case (key, jsValue: JsValue) => MongoDBObject(key -> jsValue.as[String])
      }
    ).reduce((left: DBObject, right: DBObject) => left ++ right)
  }

  @ApiOperation(value = "List file previews",
      notes = "Return the currently existing previews' basic characteristics (id, filename, content type) of the selected file.",
      responseClass = "None", httpMethod = "GET")
  def filePreviewsList(id: UUID) = SecuredAction(parse.anyContent, authorization = WithPermission(Permission.ShowFile)) {
    request =>
      files.get(id) match {
        case Some(file) => {
          val filePreviews = previews.findByFileId(file.id);
          val list = for (prv <- filePreviews) yield jsonPreview(prv)
          Ok(toJson(list))
        }
        case None => {
          Logger.error("Error getting file" + id);
          InternalServerError
        }
      }
  }

  def jsonPreview(preview: Preview): JsValue = {
    toJson(Map("id" -> preview.id.toString, "filename" -> getFilenameOrEmpty(preview), "contentType" -> preview.contentType))
  }

  def getFilenameOrEmpty(preview: Preview): String = {
    preview.filename match {
      case Some(strng) => strng
      case None => ""
    }
  }

  /**
   * Add 3D geometry file to file.
   */
  def attachGeometry(file_id: UUID, geometry_id: UUID) = SecuredAction(authorization = WithPermission(Permission.CreateFiles)) {
    request =>
      request.body match {
        case JsObject(fields) => {
          files.get(file_id) match {
            case Some(file) => {
              threeD.getGeometry(geometry_id) match {
                case Some(geometry) =>
                  threeD.updateGeometry(file_id, geometry_id, fields)
                  Ok(toJson(Map("status" -> "success")))
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
  def attachTexture(file_id: UUID, texture_id: UUID) = SecuredAction(authorization = WithPermission(Permission.CreateFiles)) {
    request =>
      request.body match {
        case JsObject(fields) => {
          files.get((file_id)) match {
            case Some(file) => {
              threeD.getTexture(texture_id) match {
                case Some(texture) => {
                  threeD.updateTexture(file_id, texture_id, fields)
                  Ok(toJson(Map("status" -> "success")))
                }
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
  @ApiOperation(value = "Add thumbnail to file", notes = "Attaches an already-existing thumbnail to a file.", responseClass = "None", httpMethod = "POST")
  def attachThumbnail(file_id: UUID, thumbnail_id: UUID) = SecuredAction(parse.anyContent, authorization = WithPermission(Permission.CreateFiles)) {
    implicit request =>
      files.get(file_id) match {
        case Some(file) => {
          thumbnails.get(thumbnail_id) match {
            case Some(thumbnail) => {
              files.updateThumbnail(file_id, thumbnail_id)
              val datasetList = datasets.findByFileId(file.id)
              for (dataset <- datasetList) {
                if (dataset.thumbnail_id.isEmpty) {
                  datasets.updateThumbnail(dataset.id, thumbnail_id)                 
                }
              }

              Ok(toJson(Map("status" -> "success")))
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
  def getGeometry(three_d_file_id: UUID, filename: String) =
    SecuredAction(parse.anyContent, authorization = WithPermission(Permission.ShowFile)) {
      request =>
        threeD.findGeometry(three_d_file_id, filename) match {
          case Some(geometry) => {

            threeD.getGeometryBlob(geometry.id) match {

              case Some((inputStream, filename, contentType, contentLength)) => {
                request.headers.get(RANGE) match {
                  case Some(value) => {
                    val range: (Long, Long) = value.substring("bytes=".length).split("-") match {
                      case x if x.length == 1 => (x.head.toLong, contentLength - 1)
                      case x => (x(0).toLong, x(1).toLong)
                    }
                    range match {
                      case (start, end) =>

                        inputStream.skip(start)
                        import play.api.mvc.{SimpleResult, ResponseHeader}
                        SimpleResult(
                          header = ResponseHeader(PARTIAL_CONTENT,
                            Map(
                              CONNECTION -> "keep-alive",
                              ACCEPT_RANGES -> "bytes",
                              CONTENT_RANGE -> "bytes %d-%d/%d".format(start, end, contentLength),
                              CONTENT_LENGTH -> (end - start + 1).toString,
                              CONTENT_TYPE -> contentType
                            )
                          ),
                          body = Enumerator.fromStream(inputStream)
                        )
                    }
                  }
                  case None => {
                    //IMPORTANT: Setting CONTENT_LENGTH header here introduces bug!                  
                    Ok.chunked(Enumerator.fromStream(inputStream))
                      .withHeaders(CONTENT_TYPE -> contentType)
                      .withHeaders(CONTENT_DISPOSITION -> ("attachment; filename=" + filename))

                  }
                }
              }
              case None => Logger.error("No geometry file found: " + geometry.id); InternalServerError("No geometry file found")

            }
          }
          case None => Logger.error("Geometry file not found"); InternalServerError
        }
    }

  /**
   * Find texture file for given 3D file and texture filename.
   */
  def getTexture(three_d_file_id: UUID, filename: String) =
    SecuredAction(parse.anyContent, authorization = WithPermission(Permission.ShowFile)) {
      request =>
        threeD.findTexture(three_d_file_id, filename) match {
          case Some(texture) => {

            threeD.getBlob(texture.id) match {

              case Some((inputStream, filename, contentType, contentLength)) => {
                request.headers.get(RANGE) match {
                  case Some(value) => {
                    val range: (Long, Long) = value.substring("bytes=".length).split("-") match {
                      case x if x.length == 1 => (x.head.toLong, contentLength - 1)
                      case x => (x(0).toLong, x(1).toLong)
                    }
                    range match {
                      case (start, end) =>

                        inputStream.skip(start)

                        SimpleResult(
                          header = ResponseHeader(PARTIAL_CONTENT,
                            Map(
                              CONNECTION -> "keep-alive",
                              ACCEPT_RANGES -> "bytes",
                              CONTENT_RANGE -> "bytes %d-%d/%d".format(start, end, contentLength),
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
                      //.withHeaders(CONTENT_LENGTH -> contentLength.toString)
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
  
  
   //Update License code 
  /**
   * REST endpoint: POST: update the license data associated with a specific File
   * 
   *  Takes one arg, id:
   *  
   *  id, the UUID associated with this file 
   *  
   *  The data contained in the request body will be containe the following key-value pairs:
   *  
   *  licenseType, currently:
   *        license1 - corresponds to Limited 
   *        license2 - corresponds to Creative Commons
   *        license3 - corresponds to Public Domain
   *        
   *  rightsHolder, currently only required if licenseType is license1. Reflects the specific name of the organization or person that holds the rights
   *   
   *  licenseText, currently tied to the licenseType
   *        license1 - Free text that a user can enter to describe the license
   *        license2 - 1 of 6 options (or their abbreviations) that reflects the specific set of 
   *        options associated with the Creative Commons license, these are:
   *            Attribution-NonCommercial-NoDerivs (by-nc-nd)
   *            Attribution-NoDerivs (by-nd)
   *            Attribution-NonCommercial (by-nc)
   *            Attribution-NonCommercial-ShareAlike (by-nc-sa)
   *            Attribution-ShareAlike (by-sa)
   *            Attribution (by)
   *        license3 - Public Domain Dedication
   *        
   *  licenseUrl, free text that a user can enter to go with the licenseText in the case of license1. Fixed URL's for the other 2 cases.
   *  
   *  allowDownload, true or false, whether the file or dataset can be downloaded. Only relevant for license1 type.  
   */
  @ApiOperation(value = "Update License information to a dataset",
      notes = "Takes four arguments, all Strings. licenseType, rightsHolder, licenseText, licenseUrl",
      responseClass = "None", httpMethod = "POST")
  def updateLicense(id: UUID) = 
    SecuredAction(parse.json, authorization = WithPermission(Permission.UpdateLicense)) {    
    implicit request =>
      if (UUID.isValid(id.stringify)) {         

          //Set up the vars we are looking for
          var licenseType: String = null;
          var rightsHolder: String = null;
          var licenseText: String = null;
          var licenseUrl: String = null;
          var allowDownload: String = null;
          
          var aResult: JsResult[String] = (request.body \ "licenseType").validate[String]
          
          // Pattern matching
          aResult match {
              case s: JsSuccess[String] => {
                licenseType = s.get
              }
              case e: JsError => {
                Logger.error("Errors: " + JsError.toFlatJson(e).toString())
                BadRequest(toJson(s"licenseType data is missing."))
              }
          }
          
          aResult = (request.body \ "rightsHolder").validate[String]
          
          // Pattern matching
          aResult match {
              case s: JsSuccess[String] => {
                rightsHolder = s.get
              }
              case e: JsError => {
                Logger.error("Errors: " + JsError.toFlatJson(e).toString())
                BadRequest(toJson(s"rightsHolder data is missing."))
              }
          }
          
          aResult = (request.body \ "licenseText").validate[String]
          
          // Pattern matching
          aResult match {
              case s: JsSuccess[String] => {                
                licenseText = s.get
                                
                //Modify the abbreviations if they were sent in that way
                if (licenseText == "by-nc-nd") {
                    licenseText = "Attribution-NonCommercial-NoDerivs"
                }
                else if (licenseText == "by-nd") {
                    licenseText = "Attribution-NoDerivs"
                }
                else if (licenseText == "by-nc") {
                    licenseText = "Attribution-NonCommercial"
                }
                else if (licenseText == "by-nc-sa") {
                    licenseText = "Attribution-NonCommercial-ShareAlike"
                }
                else if (licenseText == "by-sa") {
                    licenseText = "Attribution-ShareAlike"
                }
                else if (licenseText == "by") {
                    licenseText = "Attribution"
                }
              }
              case e: JsError => {
                Logger.error("Errors: " + JsError.toFlatJson(e).toString())
                BadRequest(toJson(s"licenseText data is missing."))
              }
          }
          
          aResult = (request.body \ "licenseUrl").validate[String]
          
          // Pattern matching
          aResult match {
              case s: JsSuccess[String] => {                
                licenseUrl = s.get
              }
              case e: JsError => {
                Logger.error("Errors: " + JsError.toFlatJson(e).toString())
                BadRequest(toJson(s"licenseUrl data is missing."))
              }
          }
          
          aResult = (request.body \ "allowDownload").validate[String]
          
          // Pattern matching
          aResult match {
              case s: JsSuccess[String] => {                
                allowDownload = s.get
              }
              case e: JsError => {
                Logger.error("Errors: " + JsError.toFlatJson(e).toString())
                BadRequest(toJson(s"allowDownload data is missing."))
              }
          }          
          
          Logger.debug(s"updateLicense for file with id  $id. Args are $licenseType, $rightsHolder, $licenseText, $licenseUrl, $allowDownload")
          
          files.updateLicense(id, licenseType, rightsHolder, licenseText, licenseUrl, allowDownload)
          Ok(Json.obj("status" -> "success"))
      } 
      else {
        Logger.error(s"The given id $id is not a valid ObjectId.")
        BadRequest(toJson(s"The given id $id is not a valid ObjectId."))
      }
  }
  
  
  
  //End, Update License code

  // ---------- Tags related code starts ------------------
  /**
   * REST endpoint: GET: gets the tag data associated with this file.
   */

  @ApiOperation(value = "Gets tags of a file", notes = "Returns a list of strings, List[String].", responseClass = "None", httpMethod = "GET")
  def getTags(id: UUID) = SecuredAction(parse.anyContent, authorization = WithPermission(Permission.ShowFile)) {
    implicit request =>
      Logger.info("Getting tags for file with id " + id)
      /* Found in testing: given an invalid ObjectId, a runtime exception
       * ("IllegalArgumentException: invalid ObjectId") occurs in Services.files.get().
       * So check it first.
       */
      if (UUID.isValid(id.stringify)) {
        files.get(id) match {
          case Some(file) => Ok(Json.obj("id" -> file.id.toString, "filename" -> file.filename,
            "tags" -> Json.toJson(file.tags.map(_.name))))
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
  def addTagsHelper(obj_type: TagCheckObjType, id: UUID, request: RequestWithUser[JsValue]): SimpleResult = {

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

  def removeTagsHelper(obj_type: TagCheckObjType, id: UUID, request: RequestWithUser[JsValue]): SimpleResult = {

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
   * REST endpoint: POST: Adds tags to a file.
   * Tag's (name, userId, extractor_id) tuple is used as a unique key.
   * In other words, the same tag names but diff userId or extractor_id are considered as diff tags,
   * so will be added.
   */
  @ApiOperation(value = "Adds tags to a file",
      notes = "Tag's (name, userId, extractor_id) tuple is used as a unique key. In other words, the same tag names but diff userId or extractor_id are considered as diff tags, so will be added.  The tags are expected as a list of strings: List[String].  An example is:<br>    curl -H 'Content-Type: application/json' -d '{\"tags\":[\"namo\", \"amitabha\"], \"extractor_id\": \"curl\"}' \"http://localhost:9000/api/files/533c2389e4b02a14f0943356/tags?key=theKey\"",
      responseClass = "None", httpMethod = "POST")
  def addTags(id: UUID) = SecuredAction(authorization = WithPermission(Permission.CreateTags)) {
    implicit request =>
      val theResponse = addTagsHelper(TagCheck_File, id, request)
  	  files.index(id)
  	  theResponse
  }

  /**
   * REST endpoint: POST: removes tags.
   * Tag's (name, userId, extractor_id) tuple is used as a unique key.
   * In other words, the same tag names but diff userId or extractor_id are considered as diff tags.
   * Current implementation enforces the restriction which only allows the tags to be removed by
   * the same user or extractor.
   */
  @ApiOperation(value = "Removes tags of a file",
      notes = "Tag's (name, userId, extractor_id) tuple is unique key. Same tag names but diff userId or extractor_id are considered diff tags. Tags can only be removed by the same user or extractor.  The tags are expected as a list of strings: List[String].",
      responseClass = "None", httpMethod = "POST")
  def removeTags(id: UUID) = SecuredAction(authorization = WithPermission(Permission.DeleteTags)) {
    implicit request =>
      val theResponse = removeTagsHelper(TagCheck_File, id, request)
  	  files.index(id)
  	  theResponse
  }

  /**
   * REST endpoint: POST: removes all tags of a file.
   * This is a big hammer -- it does not check the userId or extractor_id and
   * forcefully remove all tags for this id.  It is mainly intended for testing.
   */
  @ApiOperation(value = "Removes all tags of a file",
      notes = "This is a big hammer -- it does not check the userId or extractor_id and forcefully remove all tags for this file.  It is mainly intended for testing.",
      responseClass = "None", httpMethod = "POST")
  def removeAllTags(id: UUID) = SecuredAction(authorization = WithPermission(Permission.DeleteTags)) {
    implicit request =>
      Logger.info("Removing all tags for file with id: " + id)
      if (UUID.isValid(id.stringify)) {
        files.get(id) match {
          case Some(file) => {
            files.removeAllTags(id)
            files.index(id)
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
 
 /**
  * REST endpoint: GET  api/files/:id/extracted_metadata 
  * Returns metadata extracted so far for a file with id
  * 
  */
  @ApiOperation(value = "Provides metadata extracted for a file", notes = "", responseClass = "None", httpMethod = "GET")  
  def extract(id: UUID) = SecuredAction(parse.anyContent, authorization = WithPermission(Permission.ExtractMetadata)) { implicit request =>
    Logger.info("Getting extract info for file with id " + id)
    if (UUID.isValid(id.stringify)) {
     files.get(id) match {
        case Some(file) =>
          val jtags = FileOP.extractTags(file)
          val jpreviews = FileOP.extractPreviews(id)
          val vdescriptors=files.getVersusMetadata(id) match {
                  											  case Some(vd)=>api.routes.Files.getVersusMetadataJSON(id).toString
                  										      case None=> ""
                  											}
          Logger.debug("jtags: " + jtags.toString)
          Logger.debug("jpreviews: " + jpreviews.toString)
          Ok(Json.obj("file_id" -> id.toString, "filename" -> file.filename, "tags" -> jtags, "previews" -> jpreviews,"versus descriptors url"->vdescriptors))
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
  
  @ApiOperation(value = "Add comment to file", notes = "", responseClass = "None", httpMethod = "POST")
  def comment(id: UUID) = SecuredAction(authorization = WithPermission(Permission.CreateComments)) {
    implicit request =>
      request.user match {
        case Some(identity) => {
          (request.body \ "text").asOpt[String] match {
            case Some(text) => {
              val comment = new Comment(identity, text, file_id = Some(id))
              comments.insert(comment)
              files.index(id)
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
  @ApiOperation(value = "Is being processed",
      notes = "Return whether a file is currently being processed by a preprocessor.",
      responseClass = "None", httpMethod = "GET")
  def isBeingProcessed(id: UUID) = SecuredAction(parse.anyContent, authorization = WithPermission(Permission.ShowFile)) {
    request =>
      files.get(id) match {
        case Some(file) => {
          var isActivity = "false"
          extractions.findIfBeingProcessed(file.id) match {
            case false =>
            case true => isActivity = "true"
          }
          Ok(toJson(Map("isBeingProcessed" -> isActivity)))
        }
        case None => {
          Logger.error("Error getting file" + id);
          InternalServerError
        }
      }
  }


  def jsonPreviewsFiles(filesList: List[(models.File, Array[(java.lang.String, String, String, String, java.lang.String, String, Long)])]): JsValue = {
    val list = for (filePrevs <- filesList) yield jsonPreviews(filePrevs._1, filePrevs._2)
    toJson(list)
  }

  def jsonPreviews(prvFile: models.File, prvs: Array[(java.lang.String, String, String, String, java.lang.String, String, Long)]): JsValue = {
    val list = for (prv <- prvs) yield jsonPreview(UUID(prv._1), prv._2, prv._3, prv._4, prv._5, prv._6, prv._7)
    val listJson = toJson(list.toList)
    toJson(Map[String, JsValue]("file_id" -> JsString(prvFile.id.toString), "previews" -> listJson))
  }

  def jsonPreview(pvId: UUID, pId: String, pPath: String, pMain: String, pvRoute: java.lang.String, pvContentType: String, pvLength: Long): JsValue = {
    if (pId.equals("X3d"))
      toJson(Map("pv_id" -> pvId.stringify, "p_id" -> pId, "p_path" -> controllers.routes.Assets.at(pPath).toString,
        "p_main" -> pMain, "pv_route" -> pvRoute, "pv_contenttype" -> pvContentType, "pv_length" -> pvLength.toString,
        "pv_annotationsEditPath" -> api.routes.Previews.editAnnotation(pvId).toString,
        "pv_annotationsListPath" -> api.routes.Previews.listAnnotations(pvId).toString,
        "pv_annotationsAttachPath" -> api.routes.Previews.attachAnnotation(pvId).toString))
    else
      toJson(Map("pv_id" -> pvId.stringify, "p_id" -> pId, "p_path" -> controllers.routes.Assets.at(pPath).toString,
        "p_main" -> pMain, "pv_route" -> pvRoute, "pv_contenttype" -> pvContentType, "pv_length" -> pvLength.toString))
  }

  @ApiOperation(value = "Get file previews",
      notes = "Return the currently existing previews of the selected file (full description, including paths to preview files, previewer names etc).",
      responseClass = "None", httpMethod = "GET")
  def getPreviews(id: UUID) = SecuredAction(parse.anyContent, authorization = WithPermission(Permission.ShowFile)) {
      request =>
        files.get(id) match {
          case Some(file) => {

            val previewsFromDB = previews.findByFileId(file.id)
            val previewers = Previewers.findPreviewers
            //Logger.info("Number of previews " + previews.length);
            val files = List(file)
            //NOTE Should the following code be unified somewhere since it is duplicated in Datasets and Files for both api and controllers
            val previewslist = for (f <- files; if (!f.showPreviews.equals("None"))) yield {
              val pvf = for (p <- previewers; pv <- previewsFromDB; if (p.contentType.contains(pv.contentType))) yield {
                (pv.id.toString, p.id, p.path, p.main, api.routes.Previews.download(pv.id).toString, pv.contentType, pv.length)
              }
              if (pvf.length > 0) {
                (file -> pvf)
              } else {
                val ff = for (p <- previewers; if (p.contentType.contains(file.contentType))) yield {
                    //Change here. If the license allows the file to be downloaded by the current user, go ahead and use the 
                    //file bytes as the preview, otherwise return the String null and handle it appropriately on the front end
                    if (f.checkLicenseForDownload(request.user)) {
                        (file.id.toString, p.id, p.path, p.main, controllers.routes.Files.file(file.id) + "/blob", file.contentType, file.length)
                    }
                    else {
                        (f.id.toString, p.id, p.path, p.main, "null", f.contentType, f.length)
                    }
                }
                (file -> ff)
              }
            }

            Ok(jsonPreviewsFiles(previewslist.asInstanceOf[List[(models.File, Array[(java.lang.String, String, String, String, java.lang.String, String, Long)])]]))
          }
          case None => {
            Logger.error("Error getting file" + id);
            InternalServerError
          }
        }
    }  

    @ApiOperation(value = "Get metadata of the resource described by the file that were input as XML",
        notes = "",
        responseClass = "None", httpMethod = "GET")
    def getXMLMetadataJSON(id: UUID) = SecuredAction(parse.anyContent, authorization=WithPermission(Permission.ShowFilesMetadata)) { request =>
      files.get(id)  match {
        case Some(file) => {
          Ok(files.getXMLMetadataJSON(id))
        }
        case None => {Logger.error("Error finding file" + id); InternalServerError}      
      }
    }

    @ApiOperation(value = "Get community-generated metadata of the resource described by the file",
          notes = "",
          responseClass = "None", httpMethod = "GET")
    def getUserMetadataJSON(id: UUID) = SecuredAction(parse.anyContent, authorization=WithPermission(Permission.ShowFilesMetadata)) { request =>
     files.get(id)  match {
        case Some(file) => {
          Ok(files.getUserMetadataJSON(id))
        }
        case None => {Logger.error("Error finding file" + id); InternalServerError}      
      }
    }

    @ApiOperation(value = "Get technical metadata of the resource described by the file",
          notes = "",
          responseClass = "None", httpMethod = "GET")
    def getTechnicalMetadataJSON(id: UUID) = SecuredAction(parse.anyContent, authorization = WithPermission(Permission.ShowFilesMetadata)) {
      request =>
        files.get(id) match {
          case Some(file) => {
            Ok(files.getTechnicalMetadataJSON(id))
          }
          case None => {
            Logger.error("Error finding file" + id);
            InternalServerError
          }
        }
    }
     @ApiOperation(value = "Get Versus metadata of the resource described by the file",
          notes = "",
          responseClass = "None", httpMethod = "GET")
    def getVersusMetadataJSON(id: UUID) = SecuredAction(parse.anyContent, authorization = WithPermission(Permission.ShowFilesMetadata)) {
      request =>
        files.get(id) match {
          case Some(file) => {
             files.getVersusMetadata(id) match {
             		case Some(vd)=>{
             		    Logger.debug("versus Metadata found")
             			Ok(files.getVersusMetadata(id).get)
             		}
             		case None=>{
             		  Logger.debug("No versus Metadata found")
             			Ok("No Versus Metadata Found")
             		}
              }
          }
          case None => {
            Logger.error("Error finding file" + id);
            InternalServerError
          }
        }
    }

  @ApiOperation(value = "Delete file",
      notes = "Cascading action (removes file from any datasets containing it and deletes its previews, metadata and thumbnail).",
      responseClass = "None", httpMethod = "POST")
  def removeFile(id: UUID) = SecuredAction(parse.anyContent, authorization = WithPermission(Permission.DeleteFiles)) {
    request =>
      files.get(id) match {
        case Some(file) => {
          Logger.debug("Deleting file: " + file.filename)
          files.removeFile(id)
          current.plugin[VersusPlugin].foreach {        
            _.removeFromIndexes(id)        
          }
          current.plugin[ElasticsearchPlugin].foreach {
            _.delete("data", "file", id.stringify)
          }
          //remove file from RDF triple store if triple store is used
          configuration.getString("userdfSPARQLStore").getOrElse("no") match {
            case "yes" => {
              if (file.filename.endsWith(".xml")) {
                sqarql.removeFileFromGraphs(id, "rdfXMLGraphName")
              }
              sqarql.removeFileFromGraphs(id, "rdfCommunityGraphName")
            }
            case _ => {}
          }
          Ok(toJson(Map("status"->"success")))
          current.plugin[AdminsNotifierPlugin].foreach{
            _.sendAdminsNotification(Utils.baseUrl(request), "File","removed",id.stringify, file.filename)}
          Ok(toJson(Map("status"->"success")))
        }
        case None => Ok(toJson(Map("status" -> "error", "msg" -> "file not found")))
      }
  }

  /**
   * List datasets satisfying a user metadata search tree.
   */
  def searchFilesUserMetadata = SecuredAction(authorization = WithPermission(Permission.SearchFiles)) {
    request =>
      Logger.debug("Searching files' user metadata for search tree.")

      var searchJSON = Json.stringify(request.body)
      Logger.debug("thejsson: " + searchJSON)
      var searchTree = JsonUtil.parseJSON(searchJSON).asInstanceOf[java.util.LinkedHashMap[String, Any]]

      var searchQuery = files.searchUserMetadataFormulateQuery(searchTree)

      //searchQuery = searchQuery.reverse

      Logger.debug("Search completed. Returning files list.")

      val list = for (file <- searchQuery) yield jsonFileWithThumbnail(file)
      Logger.debug("thelist: " + toJson(list))
      Ok(toJson(list))
  }


  /**
   * List datasets satisfying a general metadata search tree.
   */
  def searchFilesGeneralMetadata = SecuredAction(authorization = WithPermission(Permission.SearchFiles)) {
    request =>
      Logger.debug("Searching files' metadata for search tree.")

      var searchJSON = Json.stringify(request.body)
      Logger.debug("thejsson: " + searchJSON)
      var searchTree = JsonUtil.parseJSON(searchJSON).asInstanceOf[java.util.LinkedHashMap[String, Any]]

      var searchQuery = files.searchAllMetadataFormulateQuery(searchTree)

      //searchQuery = searchQuery.reverse

      Logger.debug("Search completed. Returning files list.")

      val list = for (file <- searchQuery) yield jsonFileWithThumbnail(file)
      Logger.debug("thelist: " + toJson(list))
      Ok(toJson(list))
  }


  def index(id: UUID) {
    files.get(id) match {
      case Some(file) => {
        var tagListBuffer = new ListBuffer[String]()

        for (tag <- file.tags) {
          tagListBuffer += tag.name
        }

        val tagsJson = new JSONArray(tagListBuffer.toList)

        Logger.debug("tagStr=" + tagsJson);

        val commentsByFile = for (comment <- comments.findCommentsByFileId(id)) yield comment.text

        val commentJson = new JSONArray(commentsByFile)

        Logger.debug("commentStr=" + commentJson.toString())

        val usrMd = files.getUserMetadataJSON(id)
        Logger.debug("usrmd=" + usrMd)

        val techMd = files.getTechnicalMetadataJSON(id)
        Logger.debug("techmd=" + techMd)

        val xmlMd = files.getXMLMetadataJSON(id)
        Logger.debug("xmlmd=" + xmlMd)

        var fileDsId = ""
        var fileDsName = ""

        for (dataset <- datasets.findByFileId(file.id)) {
          fileDsId = fileDsId + dataset.id.toString + " %%% "
          fileDsName = fileDsName + dataset.name + " %%% "
        }
        
        val formatter = new SimpleDateFormat("dd/MM/yyyy")

        current.plugin[ElasticsearchPlugin].foreach {
          _.index("data", "file", id,
            List(("filename", file.filename), ("contentType", file.contentType),("author",file.author.fullName),("uploadDate",formatter.format(file.uploadDate)), ("datasetId", fileDsId),
              ("datasetName", fileDsName), ("tag", tagsJson.toString), ("comments", commentJson.toString),
              ("usermetadata", usrMd), ("technicalmetadata", techMd), ("xmlmetadata", xmlMd)))
        }
      }
      case None => Logger.error("File not found: " + id)
    }
  }
 
  def setNotesHTML(id: UUID) = SecuredAction(authorization=WithPermission(Permission.CreateNotes))  { implicit request =>
	  request.user match {
	    case Some(identity) => {
		    request.body.\("notesHTML").asOpt[String] match {
			    case Some(html) => {
			        files.setNotesHTML(id, html)
			        //index(id)
			        Ok(toJson(Map("status"->"success")))
			    }
			    case None => {
			    	Logger.error("no html specified.")
			    	BadRequest(toJson("no html specified."))
			    }
		    }
	    }
	    case None =>
	      Logger.error(("No user identity found in the request, request body: " + request.body))
	      BadRequest(toJson("No user identity found in the request, request body: " + request.body))
	  }
    }

}

object MustBreak extends Exception {}


