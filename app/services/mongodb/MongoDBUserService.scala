package services.mongodb

import com.mongodb.casbah.commons.MongoDBObject
import com.mongodb.casbah.commons.TypeImports._
import com.novus.salat.dao.{SalatDAO, ModelCompanion}
import models.{Role, UUID, User}
import org.bson.types.ObjectId
import securesocial.core.Identity
import services.UserService
import play.api.Play.current
import MongoContext.context
import com.mongodb.casbah.commons.MongoDBObject
import com.mongodb.casbah.Imports._

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
   * List user roles.
   */
  def listRoles(): List[Role] = {
    RoleDAO.findAll().toList
  }

  /**
   * Add new role.
   */
  def addRole(role: Role): Unit = {
    RoleDAO.insert(role)
  }

  /**
   * Find existing role.
   */
  def findRole(id: String): Option[Role] = {
    RoleDAO.findById(id)
  }

  /**
   * Delete role.
   */
  def deleteRole(id: String): Unit = {
    RoleDAO.removeById(id)
  }
}

object UserDAO extends ModelCompanion[User, ObjectId] {
  val dao = current.plugin[MongoSalatPlugin] match {
    case None => throw new RuntimeException("No MongoSalatPlugin");
    case Some(x) => new SalatDAO[User, ObjectId](collection = x.collection("social.users")) {}
  }
}

object RoleDAO extends ModelCompanion[Role, ObjectId] {

  val dao = current.plugin[MongoSalatPlugin] match {
    case None => throw new RuntimeException("No MongoSalatPlugin");
    case Some(x) => new SalatDAO[Role, ObjectId](collection = x.collection("roles")) {}
  }

  def findById(id: String): Option[Role] = {
    dao.findOne(MongoDBObject("_id" -> new ObjectId(id)))
  }

  def removeById(id: String) {
    dao.remove(MongoDBObject("_id" -> new ObjectId(id)), WriteConcern.Normal)
  }
}
