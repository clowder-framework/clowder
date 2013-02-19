package models

import org.bson.types.ObjectId
import com.novus.salat.dao.{ModelCompanion, SalatDAO}
import MongoContext.context
import play.api.Play.current
import services.MongoSalatPlugin
import java.util.Date
import com.mongodb.casbah.Imports.MongoDBObject

case class Token(
    id: Object,
    uuid: String,
    email: String, 
    creationTime: Date, 
    expirationTime: Date, 
    isSignUp: Boolean) {
    def isExpired = expirationTime.before(new Date)
}

object TokenDAO extends ModelCompanion[Token, ObjectId] {
  
  // TODO RK handle exception for instance if we switch to other DB
  val dao = current.plugin[MongoSalatPlugin] match {
    case None    => throw new RuntimeException("No MongoSalatPlugin");
    case Some(x) =>  new SalatDAO[Token, ObjectId](collection = x.collection("social.token")) {}
  }
  
  def findByUUID(uuid: String): Option[Token] = {dao.findOne(MongoDBObject("uuid"->uuid))}
}
