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

/**
 * Files sections.
 * 
 * @author Luigi Marini
 *
 */
object Sections extends Controller with ApiController {
  
  def add() = Authenticated {
    Action(parse.json) { request =>
      val doc = com.mongodb.util.JSON.parse(Json.stringify(request.body)).asInstanceOf[DBObject]
      doc.getAs[String]("file_id").map(id => doc.put("file_id", new ObjectId(id)))
      doc.put("_id", new ObjectId)
      Logger.debug("Section " + doc)
      SectionDAO.dao.collection.save(doc)
      Ok(toJson(Map("id"->doc.getAs[ObjectId]("_id").get.toString)))
    }
  }
  
  def get(id: String) = Authenticated {
    Action { request =>
      SectionDAO.findOneById(new ObjectId(id)) match {
        case Some(section) => Ok(toJson(Map("id"->section.id.toString, "startTime"->section.startTime.getOrElse(-1).toString)))
        case None => Logger.error("Section not found " + id); InternalServerError
      }
    }
  }

    def tag(id: String) = SecuredAction(parse.json, allowKey=false)  { implicit request =>
	    request.body.\("tag").asOpt[String] match {
		    case Some(tag) => {
		    	SectionDAO.tag(id, tag)
		    	Ok
		    }
		    case None => {
		    	Logger.error("no tag specified.")
		    	BadRequest
		    }
	    }
    }

	def comment(id: String) = SecuredAction(parse.json, allowKey=false)  { implicit request =>
	    request.body.\("comment").asOpt[String] match {
		    case Some(comment) => {
		    	SectionDAO.comment(id, new Comment(request.user.email.get, new Date(), comment))
		    	Ok
		    }
		    case None => {
		    	Logger.error("no tag specified.")
		    	BadRequest
		    }
	    }
    }
}