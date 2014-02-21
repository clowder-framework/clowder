package services.mongodb

import services.ExtractionService
import org.bson.types.ObjectId
import com.mongodb.casbah.commons.MongoDBObject
import models.Extraction

/**
 * Created by lmarini on 2/21/14.
 */
class MongoDBExtractionService extends ExtractionService {

  def findIfBeingProcessed(fileId: String): Boolean = {
    val allOfFile = Extraction.find(MongoDBObject("file_id" -> new ObjectId(fileId))).toList
    var extractorsArray: collection.mutable.Map[String, String] = collection.mutable.Map()
    for (currentExtraction <- allOfFile) {
      extractorsArray(currentExtraction.extractor_id) = currentExtraction.status
    }
    return extractorsArray.values.exists(_ != "DONE.")
  }
}
