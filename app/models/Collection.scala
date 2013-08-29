package models

import org.bson.types.ObjectId
import java.util.Date
import com.novus.salat.dao.{ModelCompanion, SalatDAO}
import services.MongoSalatPlugin
import com.mongodb.casbah.Imports._
import MongoContext.context
import play.api.Play.current

case class Collection (
  id: ObjectId = new ObjectId,
  name: String = "N/A",
  description: String = "N/A",
  created: Date, 
  datasets: List[Dataset] = List.empty
)

object Collection extends ModelCompanion[Collection, ObjectId]{

   // TODO RK handle exception for instance if we switch to other DB
  val dao = current.plugin[MongoSalatPlugin] match {
    case None    => throw new RuntimeException("No MongoSalatPlugin");
    case Some(x) =>  new SalatDAO[Collection, ObjectId](collection = x.collection("collections")) {}
  }
  
  def findOneByDatasetId(dataset_id: ObjectId): Option[Collection] = {
    dao.findOne(MongoDBObject("datasets._id" -> dataset_id))
  }
  
  
  
}