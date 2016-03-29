package services

import models._
import securesocial.core.Identity
import util.Direction
import util.Direction.Direction

/**
 * Service definition to interact with the users.
 *
 * Right now this is a Wrapper around SecureSocial to get access to
 * the users. There is no save option since all saves should be done
 * through securesocial right now. Eventually this should become a
 * wrapper for securesocial and we use User everywhere.
 *
 */
trait UserService  {
  def get(id: UUID): Option[User]

  def insert(model: User): Option[User]

  def update(model: User)

  def delete(id: UUID)

  /**
   * The number of objects that are available based on the filter
   */
  def count(filter: Option[String] = None): Long

  /**
   * Return a list objects that are available based on the filter as well as the other options.
   *
   * @param order the key to use to order the data, default is natural ordering of underlying implementation
   * @param direction the direction to order the data in
   * @param start the first element that should be returned based on the order key
   * @param limit the maximum number of elements to return
   * @param filter is a json representation of the filter to be applied
   *
   */
  def list(order: Option[String] = None, direction: Direction = Direction.DESC,
           start: Option[String] = None, limit: Integer = 20,
           filter: Option[String] = None): List[User]

  def list(date: Option[String], nextPage: Boolean, limit: Integer): List[User]
  /**
   * The number of users
   */
  def count(): Long = count(None)

  /**
   * List all users in the system.
   */
  def list: List[User] = list(limit=Integer.MAX_VALUE)

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
   * Update the give user profile
   */
  def updateProfile(id: UUID, profile: Profile)

  /**
   * Updates a value in the User Model
   * TODO: use UUID instead of email
   */
  def updateUserField(email: String, field: String, fieldText: Any)

  /**
   * Updates the user repository preferences.
   */
  def updateRepositoryPreferences(id: UUID, preferences: Map[String, String])
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
  def addUserToSpace(userId: UUID, role: Role, spaceId: UUID)
  
  /**
   * Remove a space from a specific user.
   * 
   * @param userId The identifier of the user that is being modified by this service
   * @param spaceId The space to be disassociated from the user
   * 
   */
  def removeUserFromSpace(userId: UUID, spaceId: UUID)
  
  /**
   * Update the role that a user has for a specific space.
   * 
   * @param userId The identifier of the user to be modified
   * @param role The new role to be associated with the user
   * @param spaceId The identifier of the space 
   * 
   */
  def changeUserRoleInSpace(userId: UUID, role: Role, spaceId: UUID)
  
  /**
   * Retrieve the role that a user has for a specific space.
   * 
   * @param userId The identifier of the user to retrieve
   * @param spaceId The identifier of the space to get the role for
   * 
   * @return The role that the user has associated with the space specified
   * 
   */
  def getUserRoleInSpace(userId: UUID, spaceId: UUID): Option[Role]
  
  /**
   * List the users that are associated with a specific space.
   * 
   * @param spaceId The identifier of the space to build a list of users for
   * 
   * @return A list of users that are associated with a space
   */
  def listUsersInSpace(spaceId: UUID): List[User]

  /**
   * List user roles.
   */
  def listRoles(): List[Role]

  /**
   * Add new role.
   */
  def addRole(role: Role)

  /**
   * Find existing role by id.
   */
  def findRole(id: String): Option[Role]

  /**
   * Find existing by name
   */
  def findRoleByName(name: String): Option[Role]

  /**
   * Delete role.
   */
  def deleteRole(id: String)

  /**
   * Update role
   */
  def updateRole(role: Role)

  /**
   * Follow a file.
   */
  def followResource(followerId: UUID, resourceRef: ResourceRef)

  /**
   * Unfollow a file.
   */
  def unfollowResource(followerId: UUID, resourceRef: ResourceRef)

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

  /**
   * return List[MiniEntity] - the top N recommendations rooted from sourceID
   */
  def getTopRecommendations(followerIDs: List[UUID], excludeIDs: List[UUID], num: Int): List[MiniEntity]
}
