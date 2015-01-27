package services.mongodb

import com.mongodb.casbah.WriteConcern
import com.mongodb.casbah.commons.MongoDBObject
import com.novus.salat.dao.{SalatDAO, ModelCompanion}
import models.{UUID, User}
import org.bson.types.ObjectId
import play.api.Logger
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

  override def followFile(email: String, fileId: UUID) {
    Logger.debug("Adding followed file " + fileId + " to user " + email)
    val user = findByEmail(email).get
    if (!user.followedFiles.contains(fileId.toString())) {
      UserDAO.update(MongoDBObject("_id" -> new ObjectId(user.id.stringify)), $push("followedFiles" -> fileId.toString()), false, false, WriteConcern.Safe)
    }
  }

  override def unfollowFile(email: String, fileId: UUID) {
    Logger.debug("Removing followed file " + fileId + " from user " + email)
    val user = findByEmail(email).get
    if (user.followedFiles.contains(fileId.toString())) {
      UserDAO.update(MongoDBObject("_id" -> new ObjectId(user.id.stringify)), $pull("followedFiles" -> fileId.toString()), false, false, WriteConcern.Safe)
    }
  }

  override def followDataset(email: String, datasetId: UUID) {
    Logger.debug("Adding followed dataset " + datasetId + " to user " + email)
    val user = findByEmail(email).get
    if (!user.followedDatasets.contains(datasetId.toString())) {
      UserDAO.update(MongoDBObject("_id" -> new ObjectId(user.id.stringify)),
        $push("followedDatasets" -> datasetId.toString()), false, false, WriteConcern.Safe)
    }
  }

  override def unfollowDataset(email: String, datasetId: UUID) {
    Logger.debug("Removing followed dataset " + datasetId + " from user " + email)
    val user = findByEmail(email).get
    if (user.followedDatasets.contains(datasetId.toString())) {
      UserDAO.update(MongoDBObject("_id" -> new ObjectId(user.id.stringify)),
        $pull("followedDatasets" -> datasetId.toString()), false, false, WriteConcern.Safe)
    }
  }

    /**
     * Adds the following relationship between two users
     */
    override def addFollowingRelationship(followeeEmail: String, followerEmail: String)
    {
      UserDAO.dao.update(MongoDBObject("email" -> followerEmail), $addToSet("followsUsers" -> followeeEmail));
      UserDAO.dao.update(MongoDBObject("email" -> followeeEmail), $addToSet("followedByUsers" -> followerEmail));
    }

    /**
     * Removes the following relationship between two users
     */
    override def removeFollowingRelationship(followeeEmail: String, followerEmail: String): Unit = {
      UserDAO.dao.update(MongoDBObject("email" -> followerEmail), $pull("followsUsers" -> followeeEmail));
      UserDAO.dao.update(MongoDBObject("email" -> followeeEmail), $pull("followedByUsers" -> followerEmail));
    }
}
  object UserDAO extends ModelCompanion[User, ObjectId] {
    val dao = current.plugin[MongoSalatPlugin] match {
      case None => throw new RuntimeException("No MongoSalatPlugin");
      case Some(x) => new SalatDAO[User, ObjectId](collection = x.collection("social.users")) {}
    }

}