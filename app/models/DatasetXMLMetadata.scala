package models

import services.MongoSalatPlugin
import com.novus.salat.dao.{ModelCompanion, SalatDAO}
import org.bson.types.ObjectId
import MongoContext.context
import play.api.Play.current
import com.mongodb.casbah.commons.MongoDBObject

case class DatasetXMLMetadata (

  xmlMetadata: Map[String, Any] = Map.empty,
  fileId: String
  
)

object DatasetXMLMetadata extends ModelCompanion[DatasetXMLMetadata, ObjectId] {
  
  val dao = current.plugin[MongoSalatPlugin] match {
    case None    => throw new RuntimeException("No MongoSalatPlugin");
    case Some(x) =>  new SalatDAO[DatasetXMLMetadata, ObjectId](collection = x.collection("datasetxmlmetadata")) {}
  }
  
}