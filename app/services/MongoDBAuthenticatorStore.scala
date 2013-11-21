/**
 *
 */
package services

import play.api.Application
import securesocial.core.AuthenticatorStore
import play.api.Play.current
import models.AuthenticatorDAO
import org.bson.types.ObjectId
import securesocial.core.Authenticator
import play.api.Logger

/**
 * Track securesocial authenticated users in MongoDB.
 * 
 * @author Luigi Marini
 *
 */
class MongoDBAuthenticatorStore(app: Application) extends AuthenticatorStore(app) {
  
  def save(authenticator: Authenticator): Either[Error, Unit] = {
    Logger.trace("Saving Authenticator " + authenticator)
    AuthenticatorDAO.save(authenticator)
    Right(())
  }
  
  def find(id: String): Either[Error, Option[Authenticator]] = {
    Logger.trace("Searching Authenticator " + id)
    Right(AuthenticatorDAO.find(id))
  }
  
  def delete(id: String): Either[Error, Unit] = {
    Logger.trace("Deleting id from Authenticator" + id)
    AuthenticatorDAO.delete(id)
    Right(())
  }

}