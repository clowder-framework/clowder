/**
 *
 */
package models
import com.novus.salat.dao.ModelCompanion
import com.novus.salat.dao.SalatDAO
import org.bson.types.ObjectId
import play.api.Play.current
import com.novus.salat._
import com.novus.salat.annotations._
import com.novus.salat.dao._
import MongoContext._
import java.util.Date
import com.mongodb.casbah.MongoConnection

import com.novus.salat.dao.{ SalatDAO, ModelCompanion }
//import org.joda.time.DateTime

/**
 * Uploaded files.
 * 
 * @author Luigi Marini
 *
 */
case class File(
    id: ObjectId = new ObjectId, 
    path: Option[String] = None, 
    filename: String, 
    uploadDate: Date, 
    contentType: String
)

object FileDAO extends ModelCompanion[File, ObjectId] {
//	val collection = mongoCollection("uploads.files")
  val collection = MongoConnection()("test")("uploads.files")
  val dao = new SalatDAO[File, ObjectId](collection = collection) {}
}