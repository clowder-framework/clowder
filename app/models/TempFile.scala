package models

import java.util.Date
import org.bson.types.ObjectId
import com.novus.salat.dao.{ModelCompanion, SalatDAO}
import MongoContext.context
import play.api.Play.current
import com.mongodb.casbah.commons.MongoDBObject
import services.mongodb.MongoSalatPlugin

case class TempFile(
	id: ObjectId = new ObjectId, 
    //id: String,
    path:Option[String]=None,
	//path: String ="", 
    filename: String, 
    uploadDate: Date, 
    contentType: String,
    length: Long = 0
)
object TempFileDAO extends ModelCompanion[TempFile, ObjectId] {
  // TODO RK handle exception for instance if we switch to other DB
  val dao = current.plugin[MongoSalatPlugin] match {
    case None    => throw new RuntimeException("No MongoSalatPlugin");
    case Some(x) =>  new SalatDAO[TempFile, ObjectId](collection = x.collection("uploadquery.files")) {}
  }
  
  def get(query_id: String): Option[TempFile] = {
    dao.findOneById(new ObjectId(query_id))
    match{
      case Some(file) => {
        val f=dao.findOne(MongoDBObject(" id" ->query_id ))
        f
      }
      case None=>None
    }
  }
}
