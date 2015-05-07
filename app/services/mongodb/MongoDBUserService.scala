package services.mongodb

import com.mongodb.casbah.commons.MongoDBObject
import com.novus.salat.dao.{SalatDAO, ModelCompanion}
import models.{UUID, User}
import org.bson.types.ObjectId
import securesocial.core.Identity
import services.UserService
import play.api.Play.current
import MongoContext.context
import com.mongodb.casbah.commons.MongoDBObject
import com.mongodb.casbah.Imports._
import models.Role
import models.UserSpaceAndRole
import models.UserSpaceAndRole
import scala.collection.mutable.ListBuffer
import play.api.Logger

/**
 * Wrapper around SecureSocial to get access to the users. There is
 * no save option since all saves should be done through securesocial
 * right now. Eventually this should become a wrapper for
 * securesocial and we use User everywhere.
 *
 * @author Rob Kooper
 */
class MongoDBUserService extends UserService {
  /**
   * Count all users
   */
  def count(): Long = {
    UserDAO.count(MongoDBObject())
  }

  /**
   * List all users in the system.
   */
  override def list(): List[User] = {
    UserDAO.dao.find(MongoDBObject()).toList
  }

  /**
   * Return a specific user based on the id provided.
   */
  override def findById(id: UUID): Option[User] = {
    UserDAO.dao.findOne(MongoDBObject("_id" -> new ObjectId(id.stringify)))
  }

  /**
   * Return a specific user based on an Identity
   */
  override def findByIdentity(identity: Identity): Option[User] = {
    UserDAO.dao.findOne(MongoDBObject("identityId.userId" -> identity.identityId.userId, "identityId.providerId" -> identity.identityId.providerId))
  }

  /**
   * Return a specific user based on an Identity
   */
  override def findByIdentity(userId: String, providerId: String): Option[User] = {
    UserDAO.dao.findOne(MongoDBObject("identityId.userId" -> userId, "identityId.providerId" -> providerId))
  }

  /**
   * Return a specific user based on the email provided.
   */
  override def findByEmail(email: String): Option[User] = {
    UserDAO.dao.findOne(MongoDBObject("email" -> email))
  }

  override def updateUserField(email: String, field: String, fieldText: Any) {
    val result = UserDAO.dao.update(MongoDBObject("email" -> email), $set(field -> fieldText));
  }

  override def addUserFriend(email: String, newFriend: String) {
    val result = UserDAO.dao.update(MongoDBObject("email" -> email), $push("friends" -> newFriend));
  }

  override def addUserDatasetView(email: String, dataset: UUID) {
    val result = UserDAO.dao.update(MongoDBObject("email" -> email), $push("viewed" -> dataset));
  }

  override def createNewListInUser(email: String, field: String, fieldList: List[Any]) {
    val result = UserDAO.dao.update(MongoDBObject("email" -> email), $set(field -> fieldList));
  }
  
  /**
   * @see app.services.UserService
   * 
   * Implementation of the UserService trait.
   * 
   */
  def addSpaceToUser(userId: UUID, role: Role, spaceId: UUID): Unit = {
      val spaceData = UserSpaceAndRole(Some(spaceId), Some(role))
      val result = UserDAO.dao.update(MongoDBObject("_id" -> new ObjectId(userId.stringify)), $push("spaceandrole" -> UserSpaceAndRoleData.toDBObject(spaceData)));  
  }
 
  /**
   * @see app.services.UserService
   * 
   * Implementation of the UserService trait.
   * 
   */
  def removeSpaceFromUser(userId: UUID, spaceId: UUID): Unit = {     
      UserDAO.dao.update(MongoDBObject("_id" -> new ObjectId(userId.stringify)),
    		  $pull("spaceandrole" ->  MongoDBObject( "m_spaceId" -> new ObjectId(spaceId.stringify))), false, false, WriteConcern.Safe)
  }
  
  /**
   * @see app.services.UserService
   * 
   * Implementation of the UserService trait.
   * 
   */
  def changeUserRoleInSpace(userId: UUID, role: Role, spaceId: UUID): Unit = {
      val spaceData = UserSpaceAndRole(Some(spaceId), Some(role))
      removeSpaceFromUser(userId, spaceId)
      val result = UserDAO.dao.update(MongoDBObject("_id" -> new ObjectId(userId.stringify)), $push("spaceandrole" -> UserSpaceAndRoleData.toDBObject(spaceData)));
  }
  
  /**
   * @see app.services.UserService
   * 
   * Implementation of the UserService trait.
   * 
   */
  def getUserRoleInSpace(userId: UUID, spaceId: UUID): Option[Role] = {
      var retRole: Option[Role] = None
      var found = false
      
      findById(userId) match {
          case Some(aUser) => {
              Logger.debug("---- aUser is " + aUser)
              for (aSpaceAndRole <- aUser.spaceandrole) {
                  Logger.debug("------ aSpaceAndRole is " + aSpaceAndRole)
                  if (!found) {
	                  aSpaceAndRole.m_spaceId match {
	                      case Some(anId) => {
	                          if (anId == spaceId) {
	                              retRole = aSpaceAndRole.m_role
	                              found = true
	                          }
	                      }
	                  }
                  }
              }
          }
          case None => Logger.debug("No user found for getRoleInSpace")
      }
      
      retRole
  }
  
  /**
   * @see app.services.UserService
   * 
   * Implementation of the UserService trait.
   * 
   */
  def listUsersInSpace(spaceId: UUID): List[User] = {
      val retList: ListBuffer[User] = ListBuffer.empty
      for (aUser <- UserDAO.dao.find(MongoDBObject())) {
         for (aSpaceAndRole <- aUser.spaceandrole) {
             aSpaceAndRole.m_spaceId match {
                 case Some(anId) => {
                     if (anId == spaceId) {
                         retList += aUser
                     }
                 }
                 case None => Logger.debug("No spaceId found for aSpaceAndRole in listUsersInSpace")
             }
         }
      }      
      retList.toList
  }
}

object UserDAO extends ModelCompanion[User, ObjectId] {
  val dao = current.plugin[MongoSalatPlugin] match {
    case None => throw new RuntimeException("No MongoSalatPlugin");
    case Some(x) => new SalatDAO[User, ObjectId](collection = x.collection("social.users")) {}
  }
}

/**
 * ModelCompanion object for the models.LicenseData class. Specific to MongoDB implementation, so should either
 * be in it's own utility class within services, or, as it is currently implemented, within one of the common
 * services classes that utilize it.
 */
object UserSpaceAndRoleData extends ModelCompanion[UserSpaceAndRole, ObjectId] {
  val dao = current.plugin[MongoSalatPlugin] match {
    case None => throw new RuntimeException("No MongoSalatPlugin");
    case Some(x) => new SalatDAO[UserSpaceAndRole, ObjectId](collection = x.collection("spaceandrole")) {}
  }
}

