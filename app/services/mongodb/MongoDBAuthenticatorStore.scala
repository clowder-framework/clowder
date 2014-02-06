/**
 *
 */
package services.mongodb

import play.api.Application
import securesocial.core.AuthenticatorStore
import models.AuthenticatorDAO
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