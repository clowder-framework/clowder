/**
 *
 */
package models

import java.util.Date
import org.bson.types.ObjectId
import com.novus.salat.dao.{ModelCompanion, SalatDAO}
import MongoContext.context
import play.api.Play.current
import services.MongoSalatPlugin

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
  // TODO RK handle exception for instance if we switch to other DB
  val dao = current.plugin[MongoSalatPlugin] match {
    case None    => throw new RuntimeException("No MongoSalatPlugin");
    case Some(x) =>  new SalatDAO[File, ObjectId](collection = x.collection("uploads.files")) {}
  }
}