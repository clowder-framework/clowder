package services.mongodb

import org.bson.types.ObjectId
import salat.dao.{ModelCompanion, SalatDAO}
import MongoContext.context
import play.api.Play.current
import securesocial.core.Identity
import services.DI

/**
 * Used to store securesocial users in MongoDB.
 *
 */
object SocialUserDAO extends ModelCompanion[Identity, ObjectId] {
  val mongos: MongoStartup = DI.injector.getInstance(classOf[MongoStartup])
  val dao = new SalatDAO[Identity, ObjectId](collection = mongos.collection("social.users")) {}
}