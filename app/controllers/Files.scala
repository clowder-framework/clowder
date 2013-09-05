package controllers

import java.io._
import models.FileMD
import play.api.Logger
import play.api.Play.current
import play.api.data.Form
import play.api.data.Forms._
import play.api.libs.iteratee._
import play.api.mvc._
import services._
import play.api.libs.concurrent.Execution.Implicits._
import play.api.libs.concurrent.Promise
import play.api.libs.iteratee.Input.{El, EOF, Empty}
import com.mongodb.casbah.gridfs.GridFS
import models.PreviewDAO
import models.SectionDAO
import java.text.SimpleDateFormat
import views.html.defaultpages.badRequest
import com.mongodb.casbah.commons.MongoDBObject
import models.FileDAO
import play.api.libs.json.JsValue
import play.api.libs.json.Json
import models.Comment
import java.util.Date
import models.File
import models.Dataset
import org.bson.types.ObjectId
import com.mongodb.casbah.Imports._
import play.api.libs.json.Json._
import play.api.libs.ws.WS
import fileutils.FilesUtils
import models.Extraction

/**
 * Manage files.
 * 
 * @author Luigi Marini
 */
object Files extends Controller with SecuredController {
  
  /**
   * Upload form.
   */
  val uploadForm = Form(
    mapping(
      "userid" -> nonEmptyText
    )(FileMD.apply)(FileMD.unapply)
   )
   
  /**
    * File info.
    */
  def file(id: String) = SecuredAction(parse.anyContent, allowKey=false, authorization=WithPermission(Permission.ShowFile)) { implicit request =>
    implicit val user = request.user
    Logger.info("GET file with id " + id)
    Services.files.getFile(id) match {
      case Some(file) => {
        val previewsFromDB = PreviewDAO.findByFileId(file.id)        
        val previewers = Previewers.searchFileSystem
        //Logger.info("Number of previews " + previews.length);
        val files = List(file)        
         val previewslist = for(f <- files) yield {
          val pvf = for(p <- previewers ; pv <- previewsFromDB; if (p.contentType.contains(pv.contentType))) yield {            
            (pv.id.toString, p.id, p.path, p.main, api.routes.Previews.download(pv.id.toString).toString, pv.contentType, pv.length)
          }        
          if (pvf.length > 0) {
            (file -> pvf)
          } else {
  	        val ff = for(p <- previewers ; if (p.contentType.contains(file.contentType))) yield {
  	          (file.id.toString, p.id, p.path, p.main, routes.Files.file(file.id.toString) + "/blob", file.contentType, file.length)
  	        }
  	        (file -> ff)
          }
        }
        val previews = Map(previewslist:_*)
        val sections = SectionDAO.findByFileId(file.id)
        val sectionsWithPreviews = sections.map { s =>
          val p = PreviewDAO.findOne(MongoDBObject("section_id"->s.id))
          s.copy(preview = p)
        }
        
        //Search whether file is currently being processed by extractor(s)
        var isActivity = false
        Extraction.findMostRecentByFileId(file.id) match{
          case Some(mostRecent) => {
            mostRecent.status match{
              case "DONE." => 
              case _ => { 
        				isActivity = true
        			  }  
            }
          }
          case None =>
        }
        
        
        Ok(views.html.file(file, id, previews, sectionsWithPreviews, isActivity))
      }
      case None => {Logger.error("Error getting file " + id); InternalServerError}
    }
  }
  
  /**
   * List a specific number of files before or after a certain date.
   */
  def list(when: String, date: String, limit: Int) = SecuredAction(parse.anyContent, allowKey=false, authorization=WithPermission(Permission.ListFiles)) { implicit request =>
    implicit val user = request.user
    var direction = "b"
    if (when != "") direction = when
    val formatter = new SimpleDateFormat("yyyy-MM-dd'T'hh:mm:ss")
    var prev, next = ""
    var files = List.empty[models.File]
    if (direction == "b") {
	    files = Services.files.listFilesBefore(date, limit)
    } else if (direction == "a") {
    	files = Services.files.listFilesAfter(date, limit)
    } else {
      badRequest
    }
    // latest object
    val latest = FileDAO.find(MongoDBObject()).sort(MongoDBObject("uploadDate" -> -1)).limit(1).toList
    // first object
    val first = FileDAO.find(MongoDBObject()).sort(MongoDBObject("uploadDate" -> 1)).limit(1).toList
    var firstPage = false
    var lastPage = false
    if (latest.size == 1) {
    	firstPage = files.exists(_.id == latest(0).id)
    	lastPage = files.exists(_.id == first(0).id)
    	Logger.debug("latest " + latest(0).id + " first page " + firstPage )
    	Logger.debug("first " + first(0).id + " last page " + lastPage )
    }
    
    if (files.size > 0) {
      if (date != "" && !firstPage) { // show prev button
    	prev = formatter.format(files.head.uploadDate)
      }
      if (!lastPage) { // show next button
    	next = formatter.format(files.last.uploadDate)
      }
    }
    Ok(views.html.filesList(files, prev, next, limit))
  }
   
  /**
   * Upload file page.
   */
  def uploadFile = SecuredAction(parse.anyContent, allowKey=false, authorization=WithPermission(Permission.CreateFiles)) { implicit request =>
    implicit val user = request.user
    Ok(views.html.upload(uploadForm))
  }
   
  /**
   * Upload file.
   */
  def upload() = SecuredAction(parse.multipartFormData, allowKey=false, authorization=WithPermission(Permission.CreateFiles)) { implicit request =>
    implicit val user = request.user
    user match {
      case Some(identity) => {
	      request.body.file("File").map { f =>        
	        Logger.debug("Uploading file " + f.filename)
	        
	        // store file       
	        val file = Services.files.save(new FileInputStream(f.ref.file), f.filename, f.contentType, identity)
	        val uploadedFile = f
	//        Thread.sleep(1000)
	        file match {
	          case Some(f) => {
	             var fileType = f.contentType
				    if(fileType.contains("/zip") || fileType.contains("/x-zip") || f.filename.endsWith(".zip")){
				          fileType = FilesUtils.getMainFileTypeOfZipFile(uploadedFile.ref.file, f.filename, "file")			          
				          if(fileType.startsWith("ERROR: ")){
				             Logger.error(fileType.substring(7))
				             InternalServerError(fileType.substring(7))
				          }			          
				        }
	            
	            // TODO RK need to replace unknown with the server name
	            val key = "unknown." + "file."+ fileType.replace(".","_").replace("/", ".")
	            // TODO RK : need figure out if we can use https
	            val host = "http://" + request.host + request.path.replaceAll("upload$", "")
	            val id = f.id.toString
	            current.plugin[RabbitmqPlugin].foreach{_.extract(ExtractorMessage(id, id, host, key, Map.empty, f.length.toString, "", ""))}
	            current.plugin[ElasticsearchPlugin].foreach{
	              _.index("data", "file", id, List(("filename",f.filename), ("contentType", f.contentType)))
	            }
	           
	             current.plugin[VersusPlugin].foreach{ _.index(f.id.toString,fileType) }
	             //current.plugin[VersusPlugin].foreach{_.build()}
	              
	                        
	            // redirect to file page]
	            Redirect(routes.Files.file(f.id.toString))  
	         }
	         case None => {
	           Logger.error("Could not retrieve file that was just saved.")
	           InternalServerError("Error uploading file")
	         }
	        }
	      }.getOrElse {
	         BadRequest("File not attached.")
	
	      }
      }
      case None => Redirect(routes.Datasets.list()).flashing("error" -> "You are not authorized to create new files.")
    }
  }

  /**
   * Download file using http://en.wikipedia.org/wiki/Chunked_transfer_encoding
   */
  def download(id: String) = SecuredAction(parse.anyContent, allowKey=false, authorization=WithPermission(Permission.DownloadFiles)) { request =>
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
  
  
  
  
  
  def uploadSelect() = Action(parse.multipartFormData) { implicit request =>
      request.body.file("File").map { f =>        
        Logger.debug("Uploading file " + f.filename)
        
        // store file       
        // TODO is this still used? if so replace null with user
        Logger.info("uploadSelect")
        val file = Services.files.save(new FileInputStream(f.ref.file), f.filename, f.contentType, null)
        val uploadedFile = f
//        Thread.sleep(1000)
        file match {
          case Some(f) => {
             var fileType = f.contentType
			    if(fileType.contains("/zip") || fileType.contains("/x-zip") || f.filename.endsWith(".zip")){
			          fileType = FilesUtils.getMainFileTypeOfZipFile(uploadedFile.ref.file, f.filename, "file")			          
			          if(fileType.startsWith("ERROR: ")){
			             Logger.error(fileType.substring(7))
			             InternalServerError(fileType.substring(7))
			          }			          
			        }
            
            // TODO RK need to replace unknown with the server name
            val key = "unknown." + "file."+ fileType.replace("/", ".")
            // TODO RK : need figure out if we can use https
            val host = "http://" + request.host + request.path.replaceAll("upload$", "")
            val id = f.id.toString
            current.plugin[RabbitmqPlugin].foreach{_.extract(ExtractorMessage(id, id, host, key, Map.empty, f.length.toString, "", ""))}
            current.plugin[ElasticsearchPlugin].foreach{
              _.index("files", "file", id, List(("filename",f.filename), ("contentType", f.contentType)))
            }
            // redirect to file page]
            // val query="http://localhost:9000/files/"+id+"/blob"  
           //  var slashindex=query.lastIndexOf('/')
             Redirect(routes.Search.findSimilar(f.id.toString))  
         }
         case None => {
           Logger.error("Could not retrieve file that was just saved.")
           InternalServerError("Error uploading file")
         }
        }
      }.getOrElse {
         BadRequest("File not attached.")
      }
  }
  
  
  /*Upload query to temporary folder*/
  
  def uploadSelectQuery() = Action(parse.multipartFormData) { implicit request =>
      request.body.file("File").map { f =>        
        Logger.debug("Uploading file " + f.filename)
        
        // store file       
        Logger.info("uploadSelectQuery")
         val file = Services.queries.save(new FileInputStream(f.ref.file), f.filename, f.contentType)
         val uploadedFile = f
//        Thread.sleep(1000)
        
        file match {
          case Some(f) => {
            var fileType = f.contentType
			    if(fileType.contains("/zip") || fileType.contains("/x-zip") || f.filename.endsWith(".zip")){
			          fileType = FilesUtils.getMainFileTypeOfZipFile(uploadedFile.ref.file, f.filename, "file")			          
			          if(fileType.startsWith("ERROR: ")){
			             Logger.error(fileType.substring(7))
			             InternalServerError(fileType.substring(7))
			          }			          
			        }
            
            // TODO RK need to replace unknown with the server name
            val key = "unknown." + "file."+ fileType.replace("/", ".")
            // TODO RK : need figure out if we can use https
            val host = "http://" + request.host + request.path.replaceAll("upload$", "")
            
            val id = f.id.toString
            val path=f.path
            current.plugin[RabbitmqPlugin].foreach{_.extract(ExtractorMessage(id, id, host, key, Map.empty, f.length.toString, "", ""))}
            current.plugin[ElasticsearchPlugin].foreach{
              _.index("files", "file", id, List(("filename",f.filename), ("contentType", f.contentType)))
            }
            // redirect to file page]
            Logger.debug("Query file id= "+id+ " path= "+path);
             Redirect(routes.Search.findSimilar(f.id.toString))  
             //Redirect(routes.Search.findSimilar(path.toString())) 
         }
         case None => {
           Logger.error("Could not retrieve file that was just saved.")
           InternalServerError("Error uploading file")
         }
        }
      }.getOrElse {
         BadRequest("File not attached.")
      }
  }

  
  
 
  /* Drag and drop */
   def uploadDragDrop() = Action(parse.multipartFormData) { implicit request =>
      request.body.file("File").map { f =>        
        Logger.debug("Uploading file " + f.filename)
        
        // store file       
      //  val file = Services.files.save(new FileInputStream(f.ref.file), f.filename, f.contentType)
        Logger.info("uploadDragDrop")
        val file = Services.queries.save(new FileInputStream(f.ref.file), f.filename, f.contentType)
        val uploadedFile = f
//        Thread.sleep(1000)
        file match {
          case Some(f) => {
             var fileType = f.contentType
			    if(fileType.contains("/zip") || fileType.contains("/x-zip") || f.filename.endsWith(".zip")){
			          fileType = FilesUtils.getMainFileTypeOfZipFile(uploadedFile.ref.file, f.filename, "file")			          
			          if(fileType.startsWith("ERROR: ")){
			             Logger.error(fileType.substring(7))
			             InternalServerError(fileType.substring(7))
			          }			          
			        }
            
            // TODO RK need to replace unknown with the server name
            val key = "unknown." + "file."+ fileType.replace(".","_").replace("/", ".")
            // TODO RK : need figure out if we can use https
            val host = "http://" + request.host + request.path.replaceAll("upload$", "")
            val id = f.id.toString
            current.plugin[RabbitmqPlugin].foreach{_.extract(ExtractorMessage(id, id, host, key, Map.empty, f.length.toString, "", ""))}
            current.plugin[ElasticsearchPlugin].foreach{
              _.index("data", "file", id, List(("filename",f.filename), ("contentType", f.contentType)))
           }
            
           Ok(f.id.toString)
            
            // redirect to file page]
           // Redirect(routes.Files.file(f.id.toString))  
         }
         case None => {
           Logger.error("Could not retrieve file that was just saved.")
           InternalServerError("Error uploading file")
         }
        }
      }.getOrElse {
         BadRequest("File not attached.")
      }
  }


  
  

  def uploaddnd(dataset_id: String) = SecuredAction(parse.multipartFormData, allowKey=false, authorization=WithPermission(Permission.CreateFiles)) { implicit request =>
    request.user match {
      case Some(identity) => {
      	Services.datasets.get(dataset_id)  match {
		  case Some(dataset) => {
			  request.body.file("File").map { f =>        
				  Logger.debug("Uploading file " + f.filename)
				  // store file
				  val file = Services.files.save(new FileInputStream(f.ref.file), f.filename,f.contentType, identity)
				  val uploadedFile = f
				  
				  // submit file for extraction			
				  file match {
				  case Some(f) => {
					  var fileType = f.contentType
					  if(fileType.contains("/zip") || fileType.contains("/x-zip") || f.filename.endsWith(".zip")){
						  fileType = FilesUtils.getMainFileTypeOfZipFile(uploadedFile.ref.file, f.filename, "dataset")			          
						  if(fileType.startsWith("ERROR: ")){
								Logger.error(fileType.substring(7))
								InternalServerError(fileType.substring(7))
								}			          
						  }
				  	  
					  // TODO RK need to replace unknown with the server name
					  val key = "unknown." + "file."+ fileType.replace(".", "_").replace("/", ".")
							  // TODO RK : need figure out if we can use https
							  val host = "http://" + request.host + request.path.replaceAll("uploaddnd/[A-Za-z0-9_]*$", "")
							  val id = f.id.toString
							  current.plugin[RabbitmqPlugin].foreach{_.extract(ExtractorMessage(id, id, host, key, Map.empty, f.length.toString, dataset_id, ""))}
					  current.plugin[ElasticsearchPlugin].foreach{
						  _.index("files", "file", id, List(("filename",f.filename), ("contentType", f.contentType)))
					  }
					  
					  // add file to dataset
					  // TODO create a service instead of calling salat directly
					  Dataset.addFile(dataset.id.toString, f)
					  
					// TODO RK need to replace unknown with the server name and dataset type
 			    	val dtkey = "unknown." + "dataset."+ "unknown"
			    	
			        current.plugin[RabbitmqPlugin].foreach{_.extract(ExtractorMessage(dataset_id, dataset_id, host, dtkey, Map.empty, f.length.toString, dataset_id, ""))}
		
					  // redirect to dataset page
					  Logger.info("Uploading Completed")
					  
					  Redirect(routes.Datasets.dataset(dataset_id)) 
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
		  case None => {Logger.error("Error getting dataset" + dataset_id); InternalServerError}
      	}
      }

      case None => {Logger.error("Error getting dataset" + dataset_id); InternalServerError}
    }
  }


  
  
  
  
  ///////////////////////////////////
  //
  // EXPERIMENTAL. WORK IN PROGRESS.
  //
  ///////////////////////////////////
  
  /**
   * Stream based uploading of files.
   */
  def uploadFileStreaming() = Action(parse.multipartFormData(myPartHandler)) {
      request => Ok("Done")
  }

  def myPartHandler: BodyParsers.parse.Multipart.PartHandler[MultipartFormData.FilePart[Result]] = {
        parse.Multipart.handleFilePart {
          case parse.Multipart.FileInfo(partName, filename, contentType) =>
            Logger.info("Part: " + partName + " filename: " + filename + " contentType: " + contentType);
            // TODO RK handle exception for instance if we switch to other DB
        Logger.info("myPartHandler")
			val files = current.plugin[MongoSalatPlugin] match {
			  case None    => throw new RuntimeException("No MongoSalatPlugin");
			  case Some(x) =>  x.gridFS("uploads")
			}
            
            //Set up the PipedOutputStream here, give the input stream to a worker thread
            val pos:PipedOutputStream = new PipedOutputStream();
            val pis:PipedInputStream  = new PipedInputStream(pos);
            val worker = new util.UploadFileWorker(pis, files);
            worker.contentType = contentType.get;
            worker.start();

//            val mongoFile = files.createFile(f.ref.file)
//            val filename = f.ref.file.getName()
//            Logger.debug("Uploading file " + filename)
//            mongoFile.filename = filename
//            mongoFile.contentType = play.api.libs.MimeTypes.forFileName(filename).getOrElse(play.api.http.ContentTypes.BINARY)
//            mongoFile.save
//            val id = mongoFile.getAs[ObjectId]("_id").get.toString
//            Ok(views.html.file(mongoFile.asDBObject, id))
            
            
            //Read content to the POS
            Iteratee.fold[Array[Byte], PipedOutputStream](pos) { (os, data) =>
              os.write(data)
              os
            }.mapDone { os =>
              os.close()
              Ok("upload done")
            }
        }
   }
  
  /**
   * Ajax upload. How do we pass in the file name?(parse.temporaryFile)
   */
  
  
  def uploadAjax = Action(parse.temporaryFile) { request =>

    val f = request.body.file
    val filename=f.getName()
    
    // store file
    // TODO is this still used? if so replace null with user.
        Logger.info("uploadAjax")
    val file = Services.files.save(new FileInputStream(f.getAbsoluteFile()), filename, None, null)
    
    file match {
      case Some(f) => {
         var fileType = f.contentType
        
        // TODO RK need to replace unknown with the server name
        val key = "unknown." + "file."+ f.contentType.replace(".", "_").replace("/", ".")
        // TODO RK : need figure out if we can use https
        val host = "http://" + request.host + request.path.replaceAll("upload$", "")
        val id = f.id.toString
        current.plugin[RabbitmqPlugin].foreach{_.extract(ExtractorMessage(id, id, host, key, Map.empty, f.length.toString, "", ""))}
        current.plugin[ElasticsearchPlugin].foreach{
          _.index("files", "file", id, List(("filename",f.filename), ("contentType", f.contentType)))
        }
        // redirect to file page
        Redirect(routes.Files.file(f.id.toString))  
      }
      case None => {
        Logger.error("Could not retrieve file that was just saved.")
        InternalServerError("Error uploading file")
      }
    }
  }
  
  /**
   * Reactive file upload.
   */
//  def reactiveUpload = Action(BodyParser(rh => new SomeIteratee)) { request =>
//     Ok("Done")
//   }
  
  /**
   * Iteratee for reactive file upload.
   * 
   * TODO Finish implementing. Right now it doesn't write to anything.
   */
// case class SomeIteratee(state: Symbol = 'Cont, input: Input[Array[Byte]] = Empty, 
//     received: Int = 0) extends Iteratee[Array[Byte], Either[Result, Int]] {
//   Logger.debug(state + " " + input + " " + received)
//
////   val files = current.plugin[MongoSalatPlugin] match {
////			  case None    => throw new RuntimeException("No MongoSalatPlugin");
////			  case Some(x) =>  x.gridFS("uploads")
////			}
////
////   val pos:PipedOutputStream = new PipedOutputStream();
////   val pis:PipedInputStream  = new PipedInputStream(pos);
////   val file = files(pis) { fh =>
////     fh.filename = "test-file.txt"
////     fh.contentType = "text/plain"
////   }
//			
//   
//   def fold[B](
//     done: (Either[Result, Int], Input[Array[Byte]]) => Promise[B],
//     cont: (Input[Array[Byte]] => Iteratee[Array[Byte], Either[Result, Int]]) => Promise[B],
//     error: (String, Input[Array[Byte]]) => Promise[B]
//   ): Promise[B] = state match {
//     case 'Done => { 
//       Logger.debug("Done with upload")
////       pos.close()
//       done(Right(received), Input.Empty) 
//     }
//     case 'Cont => cont(in => in match {
//       case in: El[Array[Byte]] => {
//         Logger.debug("Getting ready to write " +  in.e.length)
//    	 try {
////         pos.write(in.e)
//    	 } catch {
//    	   case error => Logger.error("Error writing to gridfs" + error.toString())
//    	 }
//    	 Logger.debug("Calling recursive function")
//         copy(input = in, received = received + in.e.length)
//       }
//       case Empty => {
//         Logger.debug("Empty")
//         copy(input = in)
//       }
//       case EOF => {
//         Logger.debug("EOF")
//         copy(state = 'Done, input = in)
//       }
//       case _ => {
//         Logger.debug("_")
//         copy(state = 'Error, input = in)
//       }
//     })
//     case _ => { Logger.error("Error uploading file"); error("Some error.", input) }
//   }
// }
  
  /*
   * Add comment to a file
   */
  def comment(id: String) = SecuredAction(parse.text, ajaxCall=true, allowKey=false, authorization=WithPermission(Permission.CreateComments))  { implicit request =>
  	request.user match {
  	  case Some(identity) => {
		Logger.debug("Commenting " + id + " with " + text)
		val comment = Comment(identity.id.id, new Date(), request.body)
	  	FileDAO.update(MongoDBObject("_id" -> new ObjectId(id)),
	  	    $addToSet("comments" -> Comment.toDBObject(comment)), false, false, WriteConcern.Safe)
	    Ok(toJson(""))
  	  }
  	  case None => Unauthorized("Not authorized")
  	}
  }
}
