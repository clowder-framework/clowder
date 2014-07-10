package services.mongodb

import securesocial.core.UserServicePlugin
import play.api.{Application, Logger}
import securesocial.core.providers.Token
import com.mongodb.casbah.Imports._
import securesocial.core.Identity
import org.joda.time.DateTime
import java.util.Date
import com.novus.salat.dao.{SalatDAO, ModelCompanion}
import com.mongodb.casbah.commons.TypeImports.ObjectId
import play.api.Play._
import securesocial.core.IdentityId
import scala.Some
import MongoContext.context

/**
 * SecureSocial implementation using MongoDB.
 * 
 * @author Luigi Marini
 */
case class MongoToken(
  id: Object,
  uuid: String,
  email: String,
  creationTime: Date,
  expirationTime: Date,
  isSignUp: Boolean) {
  def isExpired = expirationTime.before(new Date)
}

object TokenDAO extends ModelCompanion[MongoToken, ObjectId] {

  val dao = current.plugin[MongoSalatPlugin] match {
    case None => throw new RuntimeException("No MongoSalatPlugin");
    case Some(x) => new SalatDAO[MongoToken, ObjectId](collection = x.collection("social.token")) {}
  }

  def findByUUID(uuid: String): Option[MongoToken] = {
    dao.findOne(MongoDBObject("uuid" -> uuid))
  }
  
  def removeByUUID(uuid: String) {
    dao.remove(MongoDBObject("uuid" -> uuid), WriteConcern.Normal)
  }
}

class MongoUserService(application: Application) extends UserServicePlugin(application) {
  /**
   * Finds a SocialUser that maches the specified id
   *
   * @param id the user id
   * @return an optional user
   */
  def find(id: IdentityId):Option[Identity] = {
    Logger.trace("Searching for user " + id)
    SocialUserDAO.findOne(MongoDBObject("identityId.userId"->id.userId, "identityId.providerId"->id.providerId))
  }

  /**
   * Finds a Social user by email and provider id.
   *
   * Note: If you do not plan to use the UsernamePassword provider just provide an empty
   * implementation.
   *
   * @param email - the user email
   * @param providerId - the provider id
   * @return
   */  
  def findByEmailAndProvider(email: String, providerId: String): Option[Identity] = {
    Logger.trace("Searching for user " + email + " " + providerId)
    SocialUserDAO.findOne(MongoDBObject("email"->email, "identityId.providerId"->providerId))
  }

  /**
   * Saves the user.  This method gets called when a user logs in.
   * This is your chance to save the user information in your backing store.
   * @param user
   */
  def save(user: Identity): Identity = {
    Logger.trace("Saving user " + user)
    val query = MongoDBObject("identityId.userId"->user.identityId.userId, "identityId.providerId"->user.identityId.providerId)
    SocialUserDAO.update(query, user, true, false, WriteConcern.Normal)
    user
  }

  /**
   * Saves a token.  This is needed for users that
   * are creating an account in the system instead of using one in a 3rd party system.
   *
   * Note: If you do not plan to use the UsernamePassword provider just provide an empty
   * implementation
   *
   * @param token The token to save
   * @return A string with a uuid that will be embedded in the welcome email.
   */
  def save(token: Token) {
    Logger.trace("Saving token " + token)
    TokenDAO.save(MongoToken(new ObjectId, token.uuid, token.email, token.creationTime.toDate, token.expirationTime.toDate, token.isSignUp))
  }


  /**
   * Finds a token
   *
   * Note: If you do not plan to use the UsernamePassword provider just provide an empty
   * implementation
   *
   * @param token the token id
   * @return
   */
  def findToken(token: String): Option[Token] = {
    Logger.trace("Searching for token " + token)
    TokenDAO.findByUUID(token) match {
      case Some(t) => Some(Token(t.id.toString, t.email, new DateTime(t.creationTime), new DateTime(t.expirationTime), t.isSignUp))
      case None => None
    }
  }

  /**
   * Deletes a token
   *
   * Note: If you do not plan to use the UsernamePassword provider just provide an empty
   * implementation
   *
   * @param uuid the token id
   */
  def deleteToken(uuid: String) {
    Logger.trace("Deleting token " + uuid)
    TokenDAO.removeByUUID(uuid)
  }

  /**
   * Deletes all expired tokens
   *
   * Note: If you do not plan to use the UsernamePassword provider just provide an empty
   * implementation
   *
   */
  def deleteExpiredTokens() {
    Logger.trace("Deleting expired tokens")
    for (token <- TokenDAO.findAll) if (token.isExpired) TokenDAO.remove(token)
  }
}
