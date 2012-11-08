package controllers

import play.api._
import play.api.mvc._
import play.api.data._
import play.api.data.Forms._
import models._
import com.mongodb.casbah.Imports._
import java.io.File
import com.mongodb.casbah.gridfs.Imports._
import play.api.Play.current
import se.radley.plugin.salat._
import play.api.libs.iteratee.Enumerator
import java.io.PipedOutputStream
import java.io.PipedInputStream
import play.api.libs.iteratee.Iteratee

/**
 * Main application controller.
 * 
 * @author Luigi Marini
 */
object Application extends Controller {
  
  /**
   * Main page.
   */
  def index = Action {
    Ok(views.html.index("Application online."))
  }
  
  /**
   * Login form.
   */
  val loginForm = Form(
    mapping(
      "email" -> nonEmptyText,
      "password" -> nonEmptyText
    )(Credentials.apply)(Credentials.unapply)
   )
   
  
  /**
   * Login page.
   */
  def login = Action {
    Ok(views.html.login(loginForm))
  }
  
  /**
   * Handle login submission.
   */
  def loginSubmit = Action { implicit request =>
    loginForm.bindFromRequest.fold(
      // Form has errors, redisplay it
      errors => BadRequest(views.html.login(errors)),
      
      // We got a valid User value, display the summary
      user => Ok("Login successfull")
    )
  }
  
  /**
   * Testing action.
   */
  def testJson = Action {
    Ok("{test:1}").as(JSON)
  }
  
  /**
   * List users.
   */
  def list() = Action {
    val users = User.findAll
    Ok(views.html.list(users))
  }

  /**
   * List users by country.
   */
  def listByCountry(country: String) = Action {
    val users = User.findByCountry(country)
    Ok(views.html.list(users))
  }

  /**
   * View user.
   */
  def view(id: ObjectId) = Action {
    User.findOneById(id).map( user =>
      Ok(views.html.user(user))
    ).getOrElse(NotFound)
  }

  /**
   * Create new user.
   */
  def create(username: String) = Action {
    val user = User(
      username = username,
      password = "1234"
    )
    User.save(user)
    Ok(views.html.user(user))
  }
 
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
    mongoCollection("uploads.files").findOne(MongoDBObject("_id" -> new ObjectId(id))) match {
      case Some(file) => Ok(views.html.file(file, id))
      case None => {Logger.error("Error getting file" + id); InternalServerError}
    }
  }
  
  /**
   * List files.
   */
  def files() = Action {
    Ok(views.html.filesList(mongoCollection("uploads.files").find().toList))
  }
   
  /**
   * Upload file.
   */
  def fileNew() = Action {
    Ok(views.html.upload(uploadForm))
  }
   
  /**
   * Upload file.
   */
  def upload() = Action(parse.multipartFormData) { implicit request =>
      request.body.file("File").map { f =>
//            val filePath = "/tmp/" + file.userid + "/" + f.filename
//            f.ref.moveTo(new File(filePath), replace=true)
        val files = gridFS("uploads")
        val mongoFile = files.createFile(f.ref.file)
        val filename = f.ref.file.getName()
        Logger.info("Uploading file " + filename)
        mongoFile.filename = filename
        mongoFile.contentType = play.api.libs.MimeTypes.forFileName(filename).getOrElse(play.api.http.ContentTypes.BINARY)
        mongoFile.save
        val id = mongoFile.getAs[ObjectId]("_id").get.toString
        Redirect(routes.Application.file(id))    
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
    val files = gridFS("uploads")
    files.findOne(MongoDBObject("_id" -> new ObjectId(id))) match {
      case Some(file) => {
    	Ok.stream(Enumerator.fromStream(file.inputStream))
    	  .withHeaders(CONTENT_DISPOSITION -> ("attachment; filename=" + file.filename.getOrElse("unknown-filename")))
      }
      case None => {
        Logger.error("Error getting file" + id)
        NotFound
      }
    }
  }
  
}