/**
 *
 */
package api

import play.api.mvc.Controller
import play.api.mvc.Action
import play.api.libs.json.Json
import play.api.Logger
import models.SectionDAO
import play.api.libs.json.Json._
import com.mongodb.casbah.Imports._
import org.bson.types.ObjectId
import models.Comment
import java.util.Date
import services.Services

/**
 * Files sections.
 * 
 * @author Luigi Marini
 *
 */
object Sections extends ApiController {

  /**
   *  REST endpoint: POST: Add a section.
   *  Requires that the request body contains a valid ObjectId in the "file_id" field,
   *  otherwise returns a BadRequest.
   *  A new ObjectId is created for this section.
   */
  def add() = SecuredAction(authorization = WithPermission(Permission.AddSections)) { implicit request =>
    request.body.\("file_id").asOpt[String] match {
      case Some(file_id) => {
        /* Found in testing: given an invalid ObjectId, a runtime exception
         * ("IllegalArgumentException: invalid ObjectId") occurs in Services.files.getFile().
         * So check it first.
         */
        if (ObjectId.isValid(file_id)) {
          Services.files.getFile(file_id) match {
            case Some(file) =>
              val doc = com.mongodb.util.JSON.parse(Json.stringify(request.body)).asInstanceOf[DBObject]
              doc.getAs[String]("file_id").map(id => doc.put("file_id", new ObjectId(id)))
              doc.put("_id", new ObjectId)
              Logger.debug("Adding a section: " + doc)
              SectionDAO.dao.collection.save(doc)
              Ok(Json.obj("id" -> doc.getAs[ObjectId]("_id").get.toString))
            case None => {
              Logger.error("The file_id " + file_id + " is not found, request body: " + request.body);
              NotFound(toJson("The file_id " + file_id + " is not found."))
            }
          }
        } else {
          Logger.error("The given file_id " + file_id + " is not a valid ObjectId.")
          BadRequest(toJson("The given file_id " + file_id + " is not a valid ObjectId."))
        }
      }
      case None => {
        Logger.error("No \"file_id\" specified, request body: " + request.body)
        BadRequest(toJson("No \"file_id\" specified."))
      }
    }
  }

  /**
   *  REST endpoint: GET: get info of this section.
   */
  def get(id: String) = SecuredAction(parse.anyContent, authorization = WithPermission(Permission.GetSections)) { implicit request =>
    Logger.info("Getting info for section with id " + id)
    SectionDAO.findOneById(new ObjectId(id)) match {
      case Some(section) =>
        Ok(Json.obj("id" -> section.id.toString, "file_id" -> section.file_id.toString,
          "startTime" -> section.startTime.getOrElse(-1).toString, "tags" -> Json.toJson(section.tags.map(_.name))))
      case None => Logger.error("Section not found " + id); NotFound(toJson("Section not found, id: " + id))
    }
  }

  // ---------- Tags related code starts ------------------
  /**
   *  REST endpoint: GET: get the tag data associated with this section.
   *  Returns a JSON object of multiple fields.
   *  One returned field is "tags", containing a list of string values.
   */
  def getTags(id: String) = SecuredAction(parse.anyContent, authorization = WithPermission(Permission.ShowFile)) { implicit request =>
    Logger.info("Getting tags for section with id " + id)
    /* Found in testing: given an invalid ObjectId, a runtime exception
     * ("IllegalArgumentException: invalid ObjectId") occurs.  So check it first.
     */
    if (ObjectId.isValid(id)) {
      SectionDAO.findOneById(new ObjectId(id)) match {
        case Some(section) =>
          Ok(Json.obj("id" -> section.id.toString, "file_id" -> section.file_id.toString,
            "tags" -> Json.toJson(section.tags.map(_.name))))
        case None => {
          Logger.error("The section with id " + id + " is not found.")
          NotFound(toJson("The section with id " + id + " is not found."))
        }
      }
    } else {
      Logger.error("The given id " + id + " is not a valid ObjectId.")
      BadRequest(toJson("The given id " + id + " is not a valid ObjectId."))
    }
  }

  /**
   *  REST endpoint: POST: Add tags to a section.
   *  Requires that the request body contains a "tags" field of List[String] type.
   */
  def addTags(id: String) = SecuredAction(authorization = WithPermission(Permission.CreateTags)) { implicit request =>
  	Files.addTagsHelper(TagCheck_Section, id, request)
  }

  /**
   *  REST endpoint: POST: remove tags.
   *  Requires that the request body contains a "tags" field of List[String] type. 
   */
  def removeTags(id: String) = SecuredAction(authorization = WithPermission(Permission.DeleteTags)) { implicit request =>
  	Files.removeTagsHelper(TagCheck_Section, id, request)
  }

  /**
   *  REST endpoint: POST: remove all tags.
   */
  def removeAllTags(id: String) = SecuredAction(authorization = WithPermission(Permission.DeleteTags)) { implicit request =>
    Logger.info("Removing all tags for section with id: " + id)
    if (ObjectId.isValid(id)) {
      SectionDAO.findOneById(new ObjectId(id)) match {
        case Some(section) => {
          SectionDAO.removeAllTags(id)
          Ok(Json.obj("status" -> "success"))
        }
        case None => {
          Logger.error("The section with id " + id + " is not found.")
          NotFound(toJson("The section with id " + id + " is not found."))
        }
      }
    } else {
      Logger.error("The given id " + id + " is not a valid ObjectId.")
      BadRequest(toJson("The given id " + id + " is not a valid ObjectId."))
    }
  }
  // ---------- Tags related code ends ------------------

	def comment(id: String) = SecuredAction(authorization=WithPermission(Permission.CreateComments))  { implicit request =>
	  request.user match {
	    case Some(identity) => {
		    request.body.\("text").asOpt[String] match {
			    case Some(text) => {
			        val comment = new Comment(identity, text, section_id=Some(id))
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
	  }
    }
}