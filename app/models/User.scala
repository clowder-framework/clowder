package models

import java.util.Date

import com.mongodb.casbah.Imports.{MongoDBObject, ObjectId}
import com.novus.salat.dao.{ModelCompanion, SalatDAO}
import com.novus.salat.annotations.Key
import MongoContext.context
import play.api.Play.current
import services.MongoSalatPlugin

case class User (
  id: ObjectId = new ObjectId,
  username: String,
  password: String,
  address: Option[Address] = None,
  added: Date = new Date(),
  updated: Option[Date] = None,
  deleted: Option[Date] = None,
  friends: Option[List[String]] = None,
  @Key("company_id") company: ObjectId = new ObjectId
)

object User extends ModelCompanion[User, ObjectId] {

  // TODO RK handle exception for instance if we switch to other DB
  val dao = current.plugin[MongoSalatPlugin] match {
    case None    => throw new RuntimeException("No MongoSalatPlugin");
    case Some(x) =>  new SalatDAO[User, ObjectId](collection = x.collection("users")) {}
  }

  def findOneByUsername(username: String): Option[User] = dao.findOne(MongoDBObject("username" -> username))
  def findByCountry(country: String) = dao.find(MongoDBObject("address.country" -> country))
}
