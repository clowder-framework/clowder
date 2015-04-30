package services

import models.{UUID, User}
import securesocial.core.Identity
import models.Role

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
   * Adds a friend
   * TODO: use UUID instead of email
   */
  def addUserFriend(email: String, newFriend: String)

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
   * Add a space to a specific user.
   * 
   * @param userId The identifier of the user that is being modified by this service
   * @param spaceId The identifier of the space that is being associated with the user
   * 
   */
  def addSpaceToUser(userId: UUID, role: Role, spaceId: UUID)
  
  /**
   * Remove a space from a specific user.
   * 
   * @param userId The identifier of the user that is being modified by this service
   * @param spaceId The space to be disassociated from the user
   * 
   */
  def removeSpaceFromUser(userId: UUID, spaceId: UUID)
  
  /**
   * Update the role that a user has for a specific space
   * 
   * @param userId The identifier of the user to be modified
   * @param role The new role to be associated with the user
   * @param spaceId The identifier of the space 
   * 
   */
  def changeUserRoleInSpace(userId: UUID, role: Role, spaceId: UUID)
  
  /**
   * List the users that are associated with a specific space.
   * 
   * @param spaceId The identifier of the space to build a list of users for.
   * 
   * @return A list of users that are associated with a space.
   */
  def listUsersInSpace(spaceId: UUID): List[User]
  
}
