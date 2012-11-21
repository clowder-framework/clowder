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
import se.radley.plugin.salat._
import MongoContext._
import securesocial.core.SocialUser

object SocialUserDAO extends ModelCompanion[SocialUser, ObjectId] {
  val dao = new SalatDAO[SocialUser, ObjectId](collection = mongoCollection("social.users")) {}
  def findOneByUsername(username: String): Option[SocialUser] = dao.findOne(MongoDBObject("username" -> username))
}