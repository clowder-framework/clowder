package models

import org.bson.types.ObjectId
import com.mongodb.casbah.Imports.MongoDBObject
import com.novus.salat.dao.{ModelCompanion, SalatDAO}
import MongoContext.context
import play.api.Play.current
import securesocial.core.SocialUser
import services.MongoSalatPlugin

object SocialUserDAO extends ModelCompanion[SocialUser, ObjectId] {
  // TODO RK handle exception for instance if we switch to other DB
  val dao = current.plugin[MongoSalatPlugin] match {
    case None    => throw new RuntimeException("No MongoSalatPlugin");
    case Some(x) =>  new SalatDAO[SocialUser, ObjectId](collection = x.collection("social.users")) {}
  }

  def findOneByUsername(username: String): Option[SocialUser] = dao.findOne(MongoDBObject("username" -> username))
}