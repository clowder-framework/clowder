package models

import org.bson.types.ObjectId
import java.util.Date
import com.novus.salat.dao.{ModelCompanion, SalatDAO}
import services.MongoSalatPlugin
import com.mongodb.casbah.Imports._
import MongoContext.context
import play.api.Play.current
import services.Services

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
  
     /**
   * List all collections outside a dataset.
   */
  def listOutsideDataset(datasetId: String): List[Collection] =  { 
	Services.datasets.get(datasetId) match{
	  case Some(dataset) =>{
	    val list = for (collection <- Services.collections.listCollections(); if(!isInDataset(dataset,collection))) yield collection
	    return list.reverse
	  }
	  case None => {
	    val list = for (collection <- Services.collections.listCollections()) yield collection
        return list.reverse
	  }
	}	     
  }


  def isInDataset(dataset: Dataset, collection: Collection): Boolean = {
    for(dsColls <- dataset.collections){
      if(dsColls.id == collection.id)
        return true
    }
    return false
  }
  
  
}