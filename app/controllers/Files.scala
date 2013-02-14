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
import play.api.libs.concurrent.Promise
import play.api.libs.iteratee.Input.{El, EOF, Empty}
import com.mongodb.casbah.gridfs.GridFS
import akka.dispatch.ExecutionContext
import scala.actors.Future

/**
 * Manage files.
 * 
 * @author Luigi Marini
 */
object Files extends Controller with securesocial.core.SecureSocial {
  
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
  def file(id: String) = Action {
    Logger.info("GET file with id " + id)    
    Services.files.getFile(id) match {
      case Some(file) => Ok(views.html.file(file, id))
      case None => {Logger.error("Error getting file" + id); InternalServerError}
    }
    
  }
  
  /**
   * List files.
   */
  def list() = Action {
    Services.files.listFiles().map(f => Logger.debug(f.toString))
    Ok(views.html.filesList(Services.files.listFiles()))
  }
   
  /**
   * Upload file page.
   */
  def uploadFile = SecuredAction { implicit request =>
    Ok(views.html.upload(uploadForm))
  }
   
  /**
   * Upload file.
   */
  def upload() = Action(parse.multipartFormData) { implicit request =>
      request.body.file("File").map { f =>        
        Logger.info("Uploading file " + f.filename)
        // store file
        val id = Services.files.save(new FileInputStream(f.ref.file), f.filename)
        // submit file for extraction
        current.plugin[RabbitmqPlugin].foreach{_.extract(id)}
        // index file 
        if (current.plugin[ElasticsearchPlugin].isDefined) {
	        Services.files.getFile(id).foreach { file =>
	          current.plugin[ElasticsearchPlugin].foreach{
	            _.index("files","file",id,List(("filename",f.filename), 
	              ("contentType", file.contentType)))}
	        }
        }
        // redirect to file page
        Redirect(routes.Files.file(id))    
      }.getOrElse {
         BadRequest("File not attached.")
      }
  }
  def upload1 = Action(parse.temporaryFile) { request =>
  request.body.moveTo(new File("/tmp/picture.jpg"),true)
  Ok("File uploaded")
}
    
  /**
   * Download file using http://en.wikipedia.org/wiki/Chunked_transfer_encoding
   */
  def download(id: String) = Action {
    Services.files.get(id) match {
      case Some((inputStream, filename)) => {
    	Ok.stream(Enumerator.fromStream(inputStream))
    	  .withHeaders(CONTENT_DISPOSITION -> ("attachment; filename=" + filename))
      }
      case None => {
        Logger.error("Error getting file" + id)
        NotFound
      }
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
			val files = current.plugin[MongoSalatPlugin] match {
			  case None    => throw new RuntimeException("No MongoSalatPlugin");
			  case Some(x) =>  x.gridFS("uploads")
			}
            
            //Set up the PipedOutputStream here, give the input stream to a worker thread
            val pos:PipedOutputStream = new PipedOutputStream();
            val pis:PipedInputStream  = new PipedInputStream(pos);
            val worker:foo.UploadFileWorker = new foo.UploadFileWorker(pis, files);
            worker.contentType = contentType.get;
            worker.start();

//            val mongoFile = files.createFile(f.ref.file)
//            val filename = f.ref.file.getName()
//            Logger.info("Uploading file " + filename)
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
   * Ajax upload. How do we pass in the file name?
   */
  def uploadAjax = Action(parse.temporaryFile) { request =>
    
    //val filename = "N/A"
    val file = request.body.file
    val filename=file.getName()
    
    // store file
    val id = Services.files.save(new FileInputStream(file.getAbsoluteFile()), filename)
    // submit file for extraction
    current.plugin[RabbitmqPlugin].foreach{_.extract(id)}
    // index file 
    if (current.plugin[ElasticsearchPlugin].isDefined) {
        Services.files.getFile(id).foreach { file =>
          current.plugin[ElasticsearchPlugin].foreach{
            _.index("files","file",id,List(("filename", filename), 
              ("contentType", file.contentType)))}
        }
    }
    // redirect to file page
    Redirect(routes.Files.file(id))  
    Ok("File uploaded")
  }
  
  /**
   * Reactive file upload.
   */
  def reactiveUpload = Action(BodyParser(rh => new SomeIteratee)) { request =>
     Ok("Done")
   }
  
  /**
   * Iteratee for reactive file upload.
   * 
   * TODO Finish implementing. Right now it doesn't write to anything.
   */
 case class SomeIteratee(state: Symbol = 'Cont, input: Input[Array[Byte]] = Empty, 
     received: Int = 0) extends Iteratee[Array[Byte], Either[Result, Int]] {
   Logger.debug(state + " " + input + " " + received)

//   val files = current.plugin[MongoSalatPlugin] match {
//			  case None    => throw new RuntimeException("No MongoSalatPlugin");
//			  case Some(x) =>  x.gridFS("uploads")
//			}
//
//   val pos:PipedOutputStream = new PipedOutputStream();
//   val pis:PipedInputStream  = new PipedInputStream(pos);
//   val file = files(pis) { fh =>
//     fh.filename = "test-file.txt"
//     fh.contentType = "text/plain"
//   }
			
   
   def fold[B](
     done: (Either[Result, Int], Input[Array[Byte]]) => Promise[B],
     cont: (Input[Array[Byte]] => Iteratee[Array[Byte], Either[Result, Int]]) => Promise[B],
     error: (String, Input[Array[Byte]]) => Promise[B]
   ): Promise[B] = state match {
     case 'Done => { 
       Logger.debug("Done with upload")
//       pos.close()
       done(Right(received), Input.Empty) 
     }
     case 'Cont => cont(in => in match {
       case in: El[Array[Byte]] => {
         Logger.debug("Getting ready to write " +  in.e.length)
    	 try {
//         pos.write(in.e)
    	 } catch {
    	   case error => Logger.error("Error writing to gridfs" + error.toString())
    	 }
    	 Logger.debug("Calling recursive function")
         copy(input = in, received = received + in.e.length)
       }
       case Empty => {
         Logger.debug("Empty")
         copy(input = in)
       }
       case EOF => {
         Logger.debug("EOF")
         copy(state = 'Done, input = in)
       }
       case _ => {
         Logger.debug("_")
         copy(state = 'Error, input = in)
       }
     })
     case _ => { Logger.error("Error uploading file"); error("Some error.", input) }
   }
 }
  
}
