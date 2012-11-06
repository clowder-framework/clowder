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
      case Some(file) => Ok(views.html.file(file))
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
      val sp : Option[FileMD] = uploadForm.bindFromRequest().fold (
            errFrm => None,
            file => Some(file)
      )
      request.body.file("file").map { f =>
         sp.map { file =>
//            val filePath = "/tmp/" + file.userid + "/" + f.filename
//            f.ref.moveTo(new File(filePath), replace=true)
            
            // TODO there should be a way to get gridFS from mongo context
            val files = gridFS("uploads")
//            val fileMD = files(f.ref.file) { fh =>
//			  fh.filename = f.ref.file.getName()
//			  fh.contentType = f.contentType.getOrElse("application/octet-stream") // TODO default mime type?
//			}
            val mongoFile = files.createFile(f.ref.file)
            mongoFile.filename = f.ref.file.getName()
            mongoFile.save
            Ok(views.html.file(mongoFile.asDBObject))
         }.getOrElse{
            BadRequest("Form binding error.")
         }
      }.getOrElse {
         BadRequest("File not attached.")
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