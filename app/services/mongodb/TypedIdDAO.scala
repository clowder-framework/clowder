package services.mongodb

import org.bson.types.ObjectId
import com.novus.salat.dao.{ModelCompanion, SalatDAO}
import MongoContext.context
import play.api.Play.current
import models.TypedID

/**
 * Used to store Typed ID in MongoDB.
 *
 */
object TypedIDDAO extends ModelCompanion[TypedID, ObjectId] {
  val dao = current.plugin[MongoSalatPlugin] match {
    case None => throw new RuntimeException("No MongoSalatPlugin");
    case Some(x) => new SalatDAO[TypedID, ObjectId](collection = x.collection("TypedID")) {}
  }
}
