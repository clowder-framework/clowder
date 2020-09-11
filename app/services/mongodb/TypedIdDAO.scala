package services.mongodb

import models.TypedID
import org.bson.types.ObjectId
import salat.dao.{ModelCompanion, SalatDAO}
import services.DI
import services.mongodb.MongoContext.context

/**
 * Used to store Typed ID in MongoDB.
 *
 */
object TypedIDDAO extends ModelCompanion[TypedID, ObjectId] {
  val mongos: MongoStartup = DI.injector.getInstance(classOf[MongoStartup])
  val dao = new SalatDAO[TypedID, ObjectId](collection = mongos.collection("TypedID")) {}
}
