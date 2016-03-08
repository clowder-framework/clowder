package services.mongodb

import org.bson.types.ObjectId
import com.novus.salat.dao.{ModelCompanion, SalatDAO}
import MongoContext.context
import play.api.Play.current
import models.MiniUser
/**
  * Used to store Mini users in MongoDB.
  */
object MiniUserDAO extends ModelCompanion[MiniUser, ObjectId] {
  val dao = current.plugin[MongoSalatPlugin] match {
    case None => throw new RuntimeException("No MongoSalatPlugin");
    case Some(x) => new SalatDAO[MiniUser, ObjectId](collection = x.collection("social.miniusers")) {}
  }
}
