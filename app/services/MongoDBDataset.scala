/**
 *
 */
package services
import models.Dataset
import com.mongodb.casbah.commons.MongoDBObject
import org.bson.types.ObjectId

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
   * Get dataset.
   */
  def get(id: String): Option[Dataset] = {
    Dataset.findOneById(new ObjectId(id))
  }
}