package models

import org.bson.types.ObjectId
import com.novus.salat.dao.{ModelCompanion, SalatDAO}
import MongoContext.context
import play.api.Play.current
import services.mongodb.MongoSalatPlugin

/**
 * Feature vectors used for multimedia indexing.
 *
 * @author Luigi Marini
 */
case class MultimediaFeatures(
  id: ObjectId = new ObjectId,
  file_id: Option[ObjectId] = None,
  section_id: Option[ObjectId] = None,
  features: List[Feature])

case class Feature(
  representation: String,
  descriptor: List[Double])

object MultimediaFeaturesDAO extends ModelCompanion[MultimediaFeatures, ObjectId] {
  val dao = current.plugin[MongoSalatPlugin] match {
    case None => throw new RuntimeException("No MongoSalatPlugin");
    case Some(x) => new SalatDAO[MultimediaFeatures, ObjectId](collection = x.collection("multimedia.features")) {}
  }
}