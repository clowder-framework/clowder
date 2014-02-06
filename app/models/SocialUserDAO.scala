package models

import org.bson.types.ObjectId
import com.novus.salat.dao.{ModelCompanion, SalatDAO}
import MongoContext.context
import play.api.Play.current
import securesocial.core.Identity
import services.mongodb.MongoSalatPlugin


object SocialUserDAO extends ModelCompanion[Identity, ObjectId] {
  // TODO RK handle exception for instance if we switch to other DB
  val dao = current.plugin[MongoSalatPlugin] match {
    case None    => throw new RuntimeException("No MongoSalatPlugin");
    case Some(x) =>  new SalatDAO[Identity, ObjectId](collection = x.collection("social.users")) {}
  }
}
