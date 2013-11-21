/**
 *
 */
package services
import models.Dataset
import com.mongodb.casbah.commons.MongoDBObject
import com.mongodb.casbah.Imports._
import org.bson.types.ObjectId
import play.api.Logger
import java.text.SimpleDateFormat
import models.Collection

/**
 * Implementation of DatasetService using Mongodb.
 * 
 * @author Luigi Marini
 *
 */
trait MongoDBDataset {
  
  val collections: CollectionService = DI.injector.getInstance(classOf[CollectionService])
  
  /**
   * List all datasets in the system.
   */
  def listDatasets(): List[Dataset] = {   
    (for (dataset <- Dataset.find(MongoDBObject())) yield dataset).toList
  }
  
  /**
   * List all datasets in the system in reverse chronological order.
   */
  def listDatasetsChronoReverse(): List[Dataset] = {
     val order = MongoDBObject("created"-> -1)
     Dataset.findAll.sort(order).toList
  }
  
  /**
   * List datasets after a specified date.
   */
  def listDatasetsAfter(date: String, limit: Int): List[Dataset] = {
    val order = MongoDBObject("created"-> -1)
    if (date == "") {
      Dataset.findAll.sort(order).limit(limit).toList
    } else {
      val sinceDate = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss").parse(date)
      Logger.info("After " + sinceDate)
      Dataset.find("created" $lt sinceDate).sort(order).limit(limit).toList
    }
  }
  
  /**
   * List datasets before a specified date.
   */
  def listDatasetsBefore(date: String, limit: Int): List[Dataset] = {
    var order = MongoDBObject("created"-> -1)
    if (date == "") {
      Dataset.findAll.sort(order).limit(limit).toList
    } else {
      order = MongoDBObject("created"-> 1)
      val sinceDate = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss").parse(date)
      Logger.info("Before " + sinceDate)
      var datasetList = Dataset.find("created" $gt sinceDate).sort(order).limit(limit + 1).toList.reverse
      datasetList = datasetList.filter(_ != datasetList.last)
      datasetList
    }
  }
  
    
  /**
   * List all datasets inside a collection.
   */
  def listInsideCollection(collectionId: String) : List[Dataset] =  { 
      Collection.findOneById(new ObjectId(collectionId)) match{
        case Some(collection) => {
          val list = for (dataset <- listDatasetsChronoReverse; if(isInCollection(dataset,collection))) yield dataset
          return list
        }
        case None =>{
          return List.empty	 	  
        } 
      }
  } 
  def isInCollection(dataset: Dataset, collection: Collection): Boolean = {
    for(collDataset <- collection.datasets){
      if(collDataset.id == dataset.id)
        return true
    }
    return false
  }
  
  /**
   * Get dataset.
   */
  def get(id: String): Option[Dataset] = {
    Dataset.findOneById(new ObjectId(id))
  }
  
  /**
   * 
   */
  def getFileId(datasetId: String, filename: String): Option[String] = {
    get(datasetId) match {
      case Some(dataset) => {	  
        for (file <- dataset.files) {
          if (file.filename.equals(filename)) {
            return Some(file.id.toString)
          }
        }
        Logger.error("File does not exist in dataset" + datasetId); return None
      }
      case None => { Logger.error("Error getting dataset" + datasetId); return None }
    }
  }
}