/**
 *
 */
package models

import org.bson.types.ObjectId
import java.util.Date
import play.api.Play.current
import com.novus.salat.dao.ModelCompanion
import com.novus.salat.dao.SalatDAO
import MongoContext.context
import com.mongodb.casbah.commons.MongoDBObject
import services.mongodb.MongoSalatPlugin

/**
 * Status of extraction job.
 *
 * @author Luigi Marini
 *
 */
case class Extraction(
  id: ObjectId = new ObjectId,
  file_id: ObjectId,
  extractor_id: String,
  status: String = "N/A",
  start: Option[Date],
  end: Option[Date])

object Extraction extends ModelCompanion[Extraction, ObjectId] {
  val dao = current.plugin[MongoSalatPlugin] match {
    case None => throw new RuntimeException("No MongoSalatPlugin");
    case Some(x) => new SalatDAO[Extraction, ObjectId](collection = x.collection("extractions")) {}
  }
}