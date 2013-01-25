/**
 *
 */
package models
import org.bson.types.ObjectId
import com.novus.salat._
import com.novus.salat.annotations._
import com.novus.salat.dao._
import se.radley.plugin.salat._
import play.api.Play.current
import MongoContext._

/**
 * A dataset is a collection of files, and streams.
 * 
 * 
 * @author Luigi Marini
 *
 */
case class Dataset (
  id: ObjectId = new ObjectId,
  name: String = "N/A",
  description: String = "N/A",
  files_id: List[ObjectId] = List.empty,
  streams_id: List[ObjectId] = List.empty
)

object Dataset extends ModelCompanion[Dataset, ObjectId] {
  val dao = new SalatDAO[Dataset, ObjectId](collection = mongoCollection("dataset")) {}
}