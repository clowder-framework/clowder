package services

import models.{UUID, User}
import securesocial.core.Identity

/**
 * Service definition to interact with the users.
 *
 * Right now this is a Wrapper around SecureSocial to get access to
 * the users. There is no save option since all saves should be done
 * through securesocial right now. Eventually this should become a
 * wrapper for securesocial and we use User everywhere.
 *
 * @author Rob Kooper
 */
trait UserService {
  /**
   * The number of users
   */
  def count(): Long

  /**
   * List all users in the system.
   */
  def list: List[User]

  /**
   * Return a specific user based on the id provided.
   */
  def findById(id: UUID): Option[User]

  /**
   * Return a specific user based on an Identity
   */
  def findByIdentity(identity: Identity): Option[User]

  /**
   * Return a specific user based on an Identity
   */
  def findByIdentity(userId: String, providerId: String): Option[User]

  /**
   * Return a specific user based on the email provided.
   * @deprecated please find
   */
  def findByEmail(email: String): Option[User]

  /**
   * Updates a value in the User Model
   * TODO: use UUID instead of email
   */
  def updateUserField(email: String, field: String, fieldText: Any)

  /**
   * Adds a dataset view
   * TODO: use UUID instead of email
   */
  def addUserDatasetView(email: String, dataset: UUID)

  /**
   * Creates a new list in User Model for friends, or viewed
   * TODO: use UUID instead of email
   */
  def createNewListInUser(email: String, field: String, fieldList: List[Any])

  /**
   * Follow a file.
   */
  def followFile(followerId: UUID, fileId: UUID)

  /**
   * Unfollow a file.
   */
  def unfollowFile(followerId: UUID, fileId: UUID)

  /**
   * Follow a dataset.
   */
  def followDataset(followerId: UUID, datasetId: UUID)

  /**
   * Unfollow a dataset.
   */
  def unfollowDataset(followerId: UUID, datasetId: UUID)

  /**
   * Follow a collection.
   */
  def followCollection(followerId: UUID, collectionId: UUID)

  /**
   * Unfollow a collection.
   */
  def unfollowCollection(followerId: UUID, collectionId: UUID)

  /*
   * Follow a user.
   */
  def followUser(followeeId: UUID, followerId: UUID)

  /**
   * Unfollow a user.
   */
  def unfollowUser(followeeId: UUID, followerId: UUID)
}
