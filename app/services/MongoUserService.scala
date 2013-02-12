package services

import securesocial.core.UserServicePlugin
import securesocial.core.UserId
import play.api.{Application, Logger}
import securesocial.core.providers.Token
import com.mongodb.casbah.Imports._
import models.User
import securesocial.core.SocialUser
import securesocial.core.AuthenticationMethod
import securesocial.core.providers.UsernamePasswordProvider
import models.SocialUserDAO
import models.TokenDAO
import securesocial.core.Identity

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
  def find(id: UserId):Option[Identity] = {
    SocialUserDAO.findOne(MongoDBObject("_id._id"->id.id, "_id.providerId"->id.providerId))
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
    SocialUserDAO.findOne(MongoDBObject("email"->email, "_id.providerId"->providerId))
  }

  /**
   * Saves the user.  This method gets called when a user logs in.
   * This is your chance to save the user information in your backing store.
   * @param user
   */
  def save(user: Identity) {
    SocialUserDAO.save(user)
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
  def save(token: Token) = {
    TokenDAO.save(token)
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
    TokenDAO.findOne(MongoDBObject("uuid"->token))
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
    TokenDAO.remove(MongoDBObject("uuid"->uuid))
  }

  /**
   * Deletes all expired tokens
   *
   * Note: If you do not plan to use the UsernamePassword provider just provide an empty
   * implementation
   *
   */
  def deleteExpiredTokens() {
    for (token <- TokenDAO.findAll) if (token.isExpired) TokenDAO.remove(token)
  }
}