package models

import java.util.Date

import org.bson.types.ObjectId

import com.novus.salat.dao.ModelCompanion
import com.novus.salat.dao.SalatDAO

import MongoContext.context
import play.api.Play.current
import services.MongoSalatPlugin

/**
 * Add and remove tags
 */

case class Tag (
  
  id: ObjectId = new ObjectId,
  name: String,
  userId: Option[String],
  extractor_id: Option[String],
  created: Date
)

object Tag extends ModelCompanion[Tag, ObjectId]{
   
  val dao = current.plugin[MongoSalatPlugin] match {
    case None    => throw new RuntimeException("No MongoSalatPlugin");
    case Some(x) =>  new SalatDAO[Tag, ObjectId](collection = x.collection("tags")) {}
  }

}