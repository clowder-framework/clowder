/**
 *
 */
package services.mongodb

import play.api.Application
import securesocial.core.{AuthenticatorStore, Authenticator}
import play.api.Logger
import java.util.Date
import com.novus.salat.dao.{SalatDAO, ModelCompanion}
import com.mongodb.casbah.Imports._
import play.api.Play._
import securesocial.core.IdentityId
import scala.Some
import org.joda.time.DateTime
import MongoContext.context

/**
 * Track securesocial authenticated users in MongoDB.
 * 
 * @author Luigi Marini
 *
 */
case class LocalAuthenticator(
                               authenticatorId: String,
                               identityId: IdentityId,
                               creationDate: Date,
                               lastUsed: Date,
                               expirationDate: Date)

object AuthenticatorDAO extends ModelCompanion[LocalAuthenticator, ObjectId] {

  val dao = current.plugin[MongoSalatPlugin] match {
    case None => throw new RuntimeException("No MongoSalatPlugin");
    case Some(x) => new SalatDAO[LocalAuthenticator, ObjectId](collection = x.collection("social.authenticator")) {}
  }

  def save(authenticator: Authenticator) {
    val localAuth = LocalAuthenticator(authenticator.id, authenticator.identityId,
      authenticator.creationDate.toDate(), authenticator.lastUsed.toDate(),
      authenticator.expirationDate.toDate())
    Logger.info("Saving authenticator")
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