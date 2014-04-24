package services.mongodb

import services.ExtractionService
import models.{UUID, Extraction}
import org.bson.types.ObjectId
import play.api.Play.current
import com.novus.salat.dao.ModelCompanion
import com.novus.salat.dao.SalatDAO
import MongoContext.context
import com.mongodb.casbah.commons.MongoDBObject

/**
 * Created by lmarini on 2/21/14.
 */
class MongoDBExtractionService extends ExtractionService {

  def findIfBeingProcessed(fileId: UUID): Boolean = {
    val allOfFile = Extraction.find(MongoDBObject("file_id" -> new ObjectId(fileId.stringify))).toList
    val extractorsArray: collection.mutable.Map[String, String] = collection.mutable.Map()
    for (currentExtraction <- allOfFile) {
      extractorsArray(currentExtraction.extractor_id) = currentExtraction.status
    }
    return extractorsArray.values.exists(_ != "DONE.")
  }

  def findAll(): List[Extraction] = {
    Extraction.findAll().toList
  }

  def insert(extraction: Extraction) {
    Extraction.insert(extraction)
  }
}

object Extraction extends ModelCompanion[Extraction, ObjectId] {
  val dao = current.plugin[MongoSalatPlugin] match {
    case None => throw new RuntimeException("No MongoSalatPlugin");
    case Some(x) => new SalatDAO[Extraction, ObjectId](collection = x.collection("extractions")) {}
  }
}
