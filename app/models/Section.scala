/**
 *
 */
package models

import org.bson.types.ObjectId
import services.MongoSalatPlugin
import com.novus.salat.dao.{ModelCompanion, SalatDAO}
import org.bson.types.ObjectId
import MongoContext.context
import play.api.Play.current
import play.api.Logger
import com.mongodb.casbah.commons.MongoDBObject
/**
 * A portion of a file.
 * 
 * @author Luigi Marini
 *
 */
case class Section (
    id: ObjectId = new ObjectId,
    file_id: ObjectId = new ObjectId,
    order: Int,
    startTime: Option[Int], // in seconds
    endTime: Option[Int], // in seconds
    preview: Option[Preview]
)

object SectionDAO extends ModelCompanion[Section, ObjectId] {
  val dao = current.plugin[MongoSalatPlugin] match {
    case None    => throw new RuntimeException("No MongoSalatPlugin");
    case Some(x) =>  new SalatDAO[Section, ObjectId](collection = x.collection("sections")) {}
  }
  
  def findByFileId(id: ObjectId): List[Section] = {
    dao.find(MongoDBObject("file_id"->id)).sort(MongoDBObject("startTime"->1)).toList
  }
}