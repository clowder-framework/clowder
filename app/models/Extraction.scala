/**
 *
 */
package models

import org.bson.types.ObjectId
import java.util.Date
import play.api.Play.current
import services.MongoSalatPlugin
import com.novus.salat.dao.ModelCompanion
import com.novus.salat.dao.SalatDAO
import MongoContext.context
import com.mongodb.casbah.commons.MongoDBObject

/**
 * Status of extraction job.
 * 
 * @author Luigi Marini
 *
 */
case class Extraction (
  id: ObjectId = new ObjectId,
  file_id: ObjectId,
  extractor_id: String,
  status: String = "N/A",
  start: Option[Date],
  end: Option[Date]
)

object Extraction extends ModelCompanion[Extraction, ObjectId] {
  // TODO RK handle exception for instance if we switch to other DB
  val dao = current.plugin[MongoSalatPlugin] match {
    case None    => throw new RuntimeException("No MongoSalatPlugin");
    case Some(x) =>  new SalatDAO[Extraction, ObjectId](collection = x.collection("extractions")) {}
  }
  
  def findMostRecentByFileId(fileId: ObjectId): Option[Extraction] = {
	val allOfFile = dao.find(MongoDBObject("file_id" -> fileId)).toList
	if(allOfFile.size != 0)
	  Some(allOfFile.last)
	else
	  None	
  }
  
}