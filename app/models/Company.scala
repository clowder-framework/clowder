/**
 *
 */
package models

import org.bson.types.ObjectId
import com.novus.salat.dao.{ModelCompanion, SalatDAO}
import MongoContext.context
import play.api.Play.current
import services.mongodb.MongoSalatPlugin

/**
 * Company.
 * 
 * @author Luigi Marini
 *
 */
case class Company (
  id: ObjectId = new ObjectId,
  name: String
)

object Company extends ModelCompanion[Company, ObjectId] {
  // TODO RK handle exception for instance if we switch to other DB
  val dao = current.plugin[MongoSalatPlugin] match {
    case None    => throw new RuntimeException("No MongoSalatPlugin");
    case Some(x) =>  new SalatDAO[Company, ObjectId](collection = x.collection("company")) {}
  }
}
