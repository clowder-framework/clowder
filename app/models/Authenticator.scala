/**
 *
 */
package models

import com.novus.salat.dao.ModelCompanion
import com.novus.salat.dao.SalatDAO
import securesocial.core.Authenticator
import play.api.Play.current
import securesocial.core.IdentityId
import java.util.Date
import play.api.Logger
import com.novus.salat.global._
import com.mongodb.casbah.Imports._
import org.joda.time.DateTime
import services.mongodb.MongoSalatPlugin
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
