package services.mongodb

import securesocial.core.UserServicePlugin
import securesocial.core.IdentityId
import play.api.{Application, Logger}
import securesocial.core.providers.Token
import com.mongodb.casbah.Imports._
import models.SocialUserDAO
import models.TokenDAO
import models.{Token => MongoToken}
import securesocial.core.Identity
import com.mongodb.casbah.commons.conversions.scala.DeregisterJodaTimeConversionHelpers
import org.joda.time.DateTime

/**
 * SecureSocial implementation using MongoDB.
 * 
 * @author Luigi Marini
 */
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
    SocialUserDAO.findOne(query) match {
      case Some(user) => SocialUserDAO.update(query, user, true, false, WriteConcern.Normal)
      case None => SocialUserDAO.save(user)
    }

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
    TokenDAO.removeById(new ObjectId(uuid), WriteConcern.Normal)
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
