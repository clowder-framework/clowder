package services.mongodb

import com.mongodb.casbah.WriteConcern
import com.mongodb.casbah.commons.MongoDBObject
import com.novus.salat.dao.{SalatDAO, ModelCompanion}
import models.{UUID, User}
import org.bson.types.ObjectId
import play.api.Logger
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

  override def addUserDatasetView(email: String, dataset: UUID) {
    val result = UserDAO.dao.update(MongoDBObject("email" -> email), $push("viewed" -> dataset));
  }

  override def createNewListInUser(email: String, field: String, fieldList: List[Any]) {
    val result = UserDAO.dao.update(MongoDBObject("email" -> email), $set(field -> fieldList));
  }

  override def followFile(followerId: UUID, fileId: UUID) {
    UserDAO.dao.update(MongoDBObject("_id" -> new ObjectId(followerId.stringify)),
                        $addToSet("followedFiles" -> new ObjectId(fileId.stringify)))
  }

  override def unfollowFile(followerId: UUID, fileId: UUID) {
    UserDAO.dao.update(MongoDBObject("_id" -> new ObjectId(followerId.stringify)),
                        $pull("followedFiles" -> new ObjectId(fileId.stringify)))
  }

  override def followDataset(followerId: UUID, datasetId: UUID) {
    UserDAO.dao.update(MongoDBObject("_id" -> new ObjectId(followerId.stringify)),
                        $addToSet("followedDatasets" -> new ObjectId(datasetId.stringify)))
  }

  override def unfollowDataset(followerId: UUID, datasetId: UUID) {
    UserDAO.dao.update(MongoDBObject("_id" -> new ObjectId(followerId.stringify)),
                        $pull("followedDatasets" -> new ObjectId(datasetId.stringify)))
  }

  /**
   * Follow a collection.
   */
  override def followCollection(followerId: UUID, collectionId: UUID) {
    UserDAO.dao.update(MongoDBObject("_id" -> new ObjectId(followerId.stringify)),
                        $addToSet("followedCollections" -> new ObjectId(collectionId.stringify)))
  }

  /**
   * Unfollow a collection.
   */
  override def unfollowCollection(followerId: UUID, collectionId: UUID) {
    UserDAO.dao.update(MongoDBObject("_id" -> new ObjectId(followerId.stringify)),
                        $pull("followedCollections" -> new ObjectId(collectionId.stringify)))
  }

    /**
     * Follow a user.
     */
    override def followUser(followeeId: UUID, followerId: UUID)
    {
      UserDAO.dao.update(MongoDBObject("_id" -> new ObjectId(followerId.stringify)),
                          $addToSet("followedUsers" -> new ObjectId(followeeId.stringify)))
      UserDAO.dao.update(MongoDBObject("_id" -> new ObjectId(followeeId.stringify)),
                          $addToSet("followers" -> new ObjectId(followerId.stringify)))
    }

    /**
     * Unfollow a user.
     */
    override def unfollowUser(followeeId: UUID, followerId: UUID) {
      UserDAO.dao.update(MongoDBObject("_id" -> new ObjectId(followerId.stringify)),
                          $pull("followedUsers" -> new ObjectId(followeeId.stringify)))
      UserDAO.dao.update(MongoDBObject("_id" -> new ObjectId(followeeId.stringify)),
                          $pull("followers" -> new ObjectId(followerId.stringify)))
    }
}
  object UserDAO extends ModelCompanion[User, ObjectId] {
    val dao = current.plugin[MongoSalatPlugin] match {
      case None => throw new RuntimeException("No MongoSalatPlugin");
      case Some(x) => new SalatDAO[User, ObjectId](collection = x.collection("social.users")) {}
    }

}
