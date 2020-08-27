/**
 *
 */
package services.mongodb

import play.api.Application
import securesocial.core.{Authenticator, AuthenticatorStore}
import play.api.Logger
import java.util.Date

import salat.dao.{ModelCompanion, SalatDAO}
import com.mongodb.casbah.Imports._
import play.api.Play._
import securesocial.core.IdentityId
import scala.Some
import org.joda.time.DateTime
import MongoContext.context
import org.bson.types.ObjectId
import securesocial.core.authenticator.Authenticator
import services.DI

/**
 * Track securesocial authenticated users in MongoDB.
 *
 *
 */
case class LocalAuthenticator(
                               authenticatorId: String,
                               identityId: IdentityId,
                               creationDate: Date,
                               lastUsed: Date,
                               expirationDate: Date)

object AuthenticatorDAO extends ModelCompanion[LocalAuthenticator, ObjectId] {

  val COLLECTION = "social.authenticator"
  val mongos: MongoStartup = DI.injector.getInstance(classOf[MongoStartup])
  val dao = new SalatDAO[LocalAuthenticator, ObjectId](collection = mongos.collection(COLLECTION)) {}


  def save(authenticator: Authenticator) {
    val localAuth = LocalAuthenticator(authenticator.id, authenticator.identityId,
      authenticator.creationDate.toDate(), authenticator.lastUsed.toDate(),
      authenticator.expirationDate.toDate())
    Logger.debug("Saving authenticator")
    dao.update(MongoDBObject("authenticatorId" -> authenticator.id), localAuth, true, false, WriteConcern.Normal)
  }

  def find(id: String): Option[Authenticator] = {
    Logger.trace("Searching Authenticator " + id)
    dao.findOne(MongoDBObject("authenticatorId" -> id)) match {
      case Some(localAuth) => {
        Some(Authenticator(localAuth.authenticatorId, localAuth.identityId,
          new DateTime(localAuth.creationDate), new DateTime(localAuth.lastUsed),
          new DateTime(localAuth.expirationDate)))
      }
      case None => None
    }
  }

  def delete(id: String) {
    Logger.trace("Deleting id from Authenticator" + id)
    AuthenticatorDAO.remove(MongoDBObject("authenticatorId" -> id), WriteConcern.Normal)
  }

}
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