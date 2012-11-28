package models

import com.novus.salat.dao.ModelCompanion
import org.bson.types.ObjectId
import securesocial.core.providers.Token

import play.api.Play.current
import java.util.Date
import com.novus.salat._
import com.novus.salat.annotations._
import com.novus.salat.dao._
import com.mongodb.casbah.Imports._
import se.radley.plugin.salat._
import MongoContext._
import securesocial.core.SocialUser

object TokenDAO extends ModelCompanion[Token, ObjectId] {
  val dao = new SalatDAO[Token, ObjectId](collection = mongoCollection("social.token")) {}
}