package services

import models.{UUID, User}

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
   * List all users in the system.
   */
  def list: List[User]

  /**
   * Return a specific user based on the id provided.
   */
  def findById(id: UUID): Option[User]

  /**
   * Return a specific user based on the email provided.
   */
  def findByEmail(email: String): Option[User]
  
  /**
  * Updates a value in the User Model
  */

  def updateUserField(email: String, field: String, fieldText: Any)

  /**
  * Adds a friend
  */
  def addUserFriend(email: String, newFriend: String)
  /**
  * Adds a dataset view
  */
  def  addUserDatasetView(email: String, dataset: UUID)
  /** 
  *Creates a new list in User Model for friends, or viewed
  */
  def createNewListInUser(email: String, field: String, fieldList: List[Any])

  /**
   * Adds the following relationship between two users
   */
  def addFollowingRelationship(followeeEmail: String, followerEmail: String)

  /**
   * Removes the following relationship between two users
   */
  def removeFollowingRelationship(followeeEmail: String, followerEmail: String)
}
