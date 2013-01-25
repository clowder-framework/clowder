/**
 *
 */
package models
import org.bson.types.ObjectId
import com.novus.salat.dao.ModelCompanion
import com.novus.salat.dao.SalatDAO
import com.mongodb.casbah.Imports._
import MongoContext._
import se.radley.plugin.salat._
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
  val dao = new SalatDAO[Company, ObjectId](collection = mongoCollection("company")) {}
}