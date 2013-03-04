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

/**
 * Implementation of DatasetService using Mongodb.
 * 
 * @author Luigi Marini
 *
 */
trait MongoDBDataset {
  
  /**
   * List all datasets in the system.
   */
  def listDatasets(): List[Dataset] = {
    (for (file <- Dataset.find(MongoDBObject())) yield file).toList
  }
  
  /**
   * List datasets after a specified date.
   */
  def listDatasetsAfter(date: String, limit: Int): List[Dataset] = {
    val order = MongoDBObject("created"-> -1)
    if (date == "") {
      Dataset.findAll.sort(order).limit(limit).toList
    } else {
      val sinceDate = new SimpleDateFormat("yyyy-MM-dd'T'hh:mm:ss").parse(date)
      Logger.info("After " + sinceDate)
      Dataset.find("created" $lt sinceDate).sort(order).limit(limit).toList
    }
  }
  
  /**
   * List datasets before a specified date.
   */
  def listDatasetsBefore(date: String, limit: Int): List[Dataset] = {
    val order = MongoDBObject("created"-> -1)
    if (date == "") {
      Dataset.findAll.sort(order).limit(limit).toList
    } else {
      val sinceDate = new SimpleDateFormat("yyyy-MM-dd'T'hh:mm:ss").parse(date)
      Logger.info("Before " + sinceDate)
      Dataset.find("created" $gt sinceDate).sort(order).limit(limit).toList
    }
  }
  
  /**
   * Get dataset.
   */
  def get(id: String): Option[Dataset] = {
    Dataset.findOneById(new ObjectId(id))
  }
}