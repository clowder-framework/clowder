/**
 *
 */
package models

import org.bson.types.ObjectId
import java.util.Date
import com.novus.salat.dao.ModelCompanion
import MongoContext.context
import play.api.Play.current
import com.novus.salat.dao.SalatDAO
import services.mongodb.MongoSalatPlugin

/**
 * A stream is a sequence of objects with potentially no beginning and no end.
 * @author Luigi Marini
 *
 */
case class Stream(
  id: ObjectId = new ObjectId,
  name: String)

case class Geometry(
  geometryType: String,
  coordinates: List[Double],
  properties: Option[Map[String, String]])

case class GeoJSON(
  featureType: String,
  features: List[Geometry])

case class Datapoint(
  id: ObjectId = new ObjectId,
  time: Option[Date], location: Option[Geometry],
  data: Option[Map[String, String]],
  source: Option[String])
  
  
object Stream extends ModelCompanion[Stream, ObjectId] {
  // TODO RK handle exception for instance if we switch to other DB
  val dao = current.plugin[MongoSalatPlugin] match {
    case None    => throw new RuntimeException("No MongoSalatPlugin");
    case Some(x) =>  new SalatDAO[Stream, ObjectId](collection = x.collection("streams")) {}
  }
}
  
