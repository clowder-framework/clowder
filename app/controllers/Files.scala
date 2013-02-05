package controllers

import play.api.mvc._
import play.api.data._
import play.api.data.Forms._
import models.FileMD
import play.api.Logger
import com.mongodb.casbah.Imports._
import com.mongodb.casbah.gridfs.Imports._
import java.io.FileInputStream
import java.io.PipedOutputStream
import java.io.PipedInputStream
import play.api.Play.current
import se.radley.plugin.salat._
import play.api.libs.iteratee.Iteratee
import play.api.libs.iteratee.Enumerator
import play.libs.Akka
import akka.actor.Props
import models.SocialUserDAO
import services.RabbitmqPlugin
import services.Services
import com.typesafe.plugin._
import services.ElasticsearchPlugin


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
  def uploadFile = SecuredAction() { implicit request =>
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
            val files = gridFS("uploads")
            
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
}
