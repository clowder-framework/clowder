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

/**
 * Files sections.
 * 
 * @author Luigi Marini
 *
 */
object Sections extends Controller {
  
  def add() = Authenticated {
    Action(parse.json) { request =>
      val doc = com.mongodb.util.JSON.parse(Json.stringify(request.body)).asInstanceOf[DBObject]
      doc.getAs[String]("file_id").map(id => doc.put("file_id", new ObjectId(id)))
      Logger.debug("Section " + doc)
      SectionDAO.dao.collection.save(doc)
      Ok(toJson("success"))
    }
  }

}