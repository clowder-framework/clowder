/**
 *
 */
package models

import org.bson.types.ObjectId
import com.novus.salat.dao.{ModelCompanion, SalatDAO}
import MongoContext.context
import play.api.Play.current
import services.MongoSalatPlugin
import java.util.Date
import com.mongodb.casbah.commons.MongoDBObject

/**
 * A dataset is a collection of files, and streams.
 * 
 * 
 * @author Luigi Marini
 *
 */
case class Dataset (
  id: ObjectId = new ObjectId,
  name: String = "N/A",
  description: String = "N/A",
  created: Date, 
  files: List[File] = List.empty,
  streams_id: List[ObjectId] = List.empty,
  tags: List[String] = List.empty
)

object Dataset extends ModelCompanion[Dataset, ObjectId] {
  // TODO RK handle exception for instance if we switch to other DB
  val dao = current.plugin[MongoSalatPlugin] match {
    case None    => throw new RuntimeException("No MongoSalatPlugin");
    case Some(x) =>  new SalatDAO[Dataset, ObjectId](collection = x.collection("datasets")) {}
  }
  
  def findOneByFileId(file_id: ObjectId): Option[Dataset] = {
    dao.findOne(MongoDBObject("files._id" -> file_id))
  }
}
