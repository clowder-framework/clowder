package models

import java.util.Date

import com.mongodb.casbah.Imports.MongoDBObject
import com.novus.salat.dao.{ModelCompanion, SalatDAO}
import com.novus.salat.annotations.Key
import services.mongodb.MongoContext
import MongoContext.context
import play.api.Play.current
import services.mongodb.MongoSalatPlugin
import com.mongodb.casbah.commons.TypeImports.ObjectId

/**
 * Generic user information. Currently not being used. Keep as reference for future work.
 * Currently using SecureSocial User to manage basic user information.
 *
 * @author Luigi Marini
 */
case class User(
  id: UUID = UUID.generate,
  username: String,
  password: String,
  address: Option[Address] = None,
  added: Date = new Date(),
  updated: Option[Date] = None,
  deleted: Option[Date] = None,
  friends: Option[List[String]] = None,
  @Key("company_id") company: Option[UUID] = None)

case class Address(
  street: String,
  zip: String,
  country: String)

case class Company(
  id: UUID = UUID.generate,
  name: String)

object Company extends ModelCompanion[Company, ObjectId] {
  val dao = current.plugin[MongoSalatPlugin] match {
    case None => throw new RuntimeException("No MongoSalatPlugin");
    case Some(x) => new SalatDAO[Company, ObjectId](collection = x.collection("company")) {}
  }
}

object User extends ModelCompanion[User, ObjectId] {

  val dao = current.plugin[MongoSalatPlugin] match {
    case None => throw new RuntimeException("No MongoSalatPlugin");
    case Some(x) => new SalatDAO[User, ObjectId](collection = x.collection("users")) {}
  }

  def findOneByUsername(username: String): Option[User] = dao.findOne(MongoDBObject("username" -> username))

  def findByCountry(country: String) = dao.find(MongoDBObject("address.country" -> country))
}

