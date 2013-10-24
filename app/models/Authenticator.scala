/**
 *
 */
package models

import com.novus.salat.dao.ModelCompanion
import services.MongoSalatPlugin
import com.novus.salat.dao.SalatDAO
import securesocial.core.Authenticator
import org.bson.types.ObjectId
import play.api.Play.current
import MongoContext.context
import securesocial.core.IdentityId
import java.util.Date
import play.api.Logger
import com.novus.salat._
import com.novus.salat.global._
import com.mongodb.casbah.Imports._
import org.joda.time.DateTime

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
  // TODO RK handle exception for instance if we switch to other DB
  val dao = current.plugin[MongoSalatPlugin] match {
    case None => throw new RuntimeException("No MongoSalatPlugin");
    case Some(x) => new SalatDAO[LocalAuthenticator, ObjectId](collection = x.collection("social.authenticator")) {}
  }

  def save(authenticator: Authenticator) {
    val localAuth = LocalAuthenticator(authenticator.id, authenticator.identityId,
      authenticator.creationDate.toDate(), authenticator.lastUsed.toDate(),
      authenticator.expirationDate.toDate())
    dao.save(localAuth)
  }

  def find(id: String): Option[Authenticator] = {
    Logger.debug("Searching Authenticator " + id)
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
    Logger.debug("Deleting id from Authenticator" + id)
    AuthenticatorDAO.remove(MongoDBObject("authenticatorId" -> id), WriteConcern.Normal)
  }

}