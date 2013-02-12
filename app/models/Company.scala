/**
 *
 */
package models
import org.bson.types.ObjectId
import com.novus.salat.dao.ModelCompanion
import com.novus.salat.dao.SalatDAO
import com.mongodb.casbah.Imports._
import MongoContext._
import play.api.Play.current

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
  val collection = MongoConnection()("test")("company")
  val dao = new SalatDAO[Company, ObjectId](collection) {}
}