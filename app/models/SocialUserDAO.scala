package models

import com.novus.salat.dao.ModelCompanion
import com.novus.salat.dao.SalatDAO
import org.bson.types.ObjectId

import play.api.Play.current
import java.util.Date
import com.novus.salat._
import com.novus.salat.annotations._
import com.novus.salat.dao._
import com.mongodb.casbah.Imports._
import MongoContext._
import securesocial.core.SocialUser
import securesocial.core.Identity
import securesocial.core.providers.utils.RoutesHelper

object SocialUserDAO extends ModelCompanion[Identity, ObjectId] {
  val dao = new SalatDAO[Identity, ObjectId](collection = MongoConnection()("test")("social.users")) {}
  def findOneByUsername(username: String): Option[Identity] = dao.findOne(MongoDBObject("username" -> username))
}