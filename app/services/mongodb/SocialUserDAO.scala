package services.mongodb

import org.bson.types.ObjectId
import com.novus.salat.dao.{ModelCompanion, SalatDAO}
import MongoContext.context
import play.api.Play.current
import securesocial.core.Identity

/**
 * Used to store securesocial users in MongoDB.
 *
 * @author Luigi Marini
 */
object SocialUserDAO extends ModelCompanion[Identity, ObjectId] {
  val dao = current.plugin[MongoSalatPlugin] match {
    case None => throw new RuntimeException("No MongoSalatPlugin");
    case Some(x) => new SalatDAO[Identity, ObjectId](collection = x.collection("social.users")) {}
  }
}
