package services

import api.Permission.Permission
import models._
import models.Collection
import models.Dataset
import models.User
import models.Role

/**
 * Service to manipulate spaces.
 */
trait SpaceService {
  /** return space with specific id */
  def get(id: UUID): Option[ProjectSpace]

  def get(ids: List[UUID]): DBResult[ProjectSpace]

  /** insert new space, will return id if successful. */
  def insert(model: ProjectSpace): Option[String]

  /** update space */
  def update(model: ProjectSpace)

  /** delete given space. */
  def delete(id: UUID, host: String, apiKey: Option[String], user: Option[User])

  /** Count all spaces */
  def count(): Long

  /** list all spaces */
  def list(): List[ProjectSpace]

  /**
   * Return a count of spaces the user has access to.
   */
  def countAccess(permisions: Set[Permission], user: Option[User], showAll: Boolean): Long

  /**
   * Return a list of spaces the user has access to.
   */
  def listAccess(limit: Integer, permisions: Set[Permission], user: Option[User], showAll: Boolean, showPublic: Boolean, onlyTrial: Boolean, showOnlyShared : Boolean): List[ProjectSpace]

  /**
   * Return a list of spaces the user has access to matching title.
   */
  def listAccess(limit: Integer, title: String, permisions: Set[Permission], user: Option[User], showAll: Boolean, showPublic: Boolean, showOnlyShared : Boolean): List[ProjectSpace]

  /**
   * Return a list of spaces the user has access to starting at a specific date.
   */
  def listAccess(date: String, nextPage: Boolean, limit: Integer, permisions: Set[Permission], user: Option[User], showAll: Boolean, showPublic: Boolean, onlyTrial: Boolean, showOnlyShared : Boolean): List[ProjectSpace]

  /**
   * Return a list of spaces the user has access to starting at a specific date and  matching title.
   */
  def listAccess(date: String, nextPage: Boolean, limit: Integer, title: String, permisions: Set[Permission], user: Option[User], showAll: Boolean, showPublic: Boolean, showOnlyShared : Boolean): List[ProjectSpace]

  /**
   * Return a count of spaces the user has created.
   */
  def countUser(user: Option[User], showAll: Boolean, owner: User): Long

  /**
   * Return a list of spaces the user has created.
   */
  def listUser(limit: Integer, user: Option[User], showAll: Boolean, owner: User): List[ProjectSpace]

  /**
   * Return a list of spaces the user has created with matching title.
   */
  def listUser(limit: Integer, title: String, user: Option[User], showAll: Boolean, owner: User): List[ProjectSpace]

  /**
   * Return a list of spaces the user has created starting at a specific date.
   */
  def listUser(date: String, nextPage: Boolean, limit: Integer, user: Option[User], showAll: Boolean, owner: User): List[ProjectSpace]

  /**
    * Return a list of spaces the user has created starting at a specific date with matching title.
    */
  def listUser(date: String, nextPage: Boolean, limit: Integer, title: String, user: Option[User], showAll: Boolean, owner: User): List[ProjectSpace]

  /**
    * Return a list of spaces with specific status
    */
  def listByStatus(status: String):List[ProjectSpace]

  def addCollection(collection: UUID, space: UUID, user : Option[User])

  def removeCollection(collection: UUID, space:UUID)

  def decrementCollectionCounter(collection: UUID, space: UUID, decrement: Int)

  def incrementCollectionCounter(collection: UUID, space: UUID, increment: Int)

  def addDataset(dataset: UUID, space: UUID)

  def removeDataset(dataset:UUID, space: UUID)

  /**
   * Determine if time to live for resources is enabled for a specific space.
   *
   * @param space The identifier for the space that is being checked
   *
   * @return A flag that denotes if time to live is enabled on this space.
   */
  def isTimeToLiveEnabled(space: UUID): Boolean

  /**
   * Obtain the time to live for resources that are assigned to a specific space.
   *
   * @param space The identifier for the space to be queried
   *
   * @return The length of time, in milliseconds, that resources are allowed to persist in this space.
   *
   */
  def getTimeToLive(space: UUID): Long

  /**
   * Service call to tell a space to clean up resources that are expired relative to the
   * specified time to live.
   *
   * @param space The identifier for the space that will be purged
   *
   */
  def purgeExpiredResources(space: UUID, host: String, apiKey: Option[String], user: Option[User])

  /**
   * Service access to retrieve a list of collections in a given space, of prescribed list length.
   *
   * @param space Identifies the space.
   * @param limit Length of (the number of collections in) returned list.
   *
   * @return A list of collections in a space; list's length is defined by 'limit'.
   */
  def getCollectionsInSpace(space: Option[String] = None, limit: Option[Integer] = None): List[Collection]

  /**
   * Service access to retrieve a list of datasets in a given space, of prescribed list length.
   *
   * @param space Identifies the space.
   * @param limit Length of (the number of datasets in) returned list.
   *
   * @return A list of datasets in a space; list's length is defined by 'limit'.
   */
  def getDatasetsInSpace(space: Option[String] = None, limit: Option[Integer] = None): List[Dataset]

  /**
   * Service call to update the information and configuration that are part of a space.
   *
   * @param spaceId The identifier for the space to be updated
   * @param name The updated name information, HTMLEncoded since it is free text
   * @param description The updated description information, HTMLEncoded since it is free text
   * @param timeToLive The updated amount of time, in milliseconds, that resources should be preserved in the space
   * @param expireEnabled The updated flag, indicating whether or not the space should allow resources to expire*
   * @param access The updated flag indicate the space is private or public
   */
  def updateSpaceConfiguration(spaceId: UUID, name: String, description: String, timeToLive: Long, expireEnabled: Boolean, access:String)

  /**
   * Add a user to the space, along with an associated role.
   *
   * @param user The identifier for the user that is to be added to the space
   * @param role The role that is to be assigned to the user in the context of this space
   * @param space The identifier for the space that the user is being added to
   *
   */
  def addUser(user: UUID, role: Role, space: UUID)

  /**
   * Remove a user from the space.
   *
   * @param userId The identifier of the user to be removed from the space
   * @param space The identifier for the space that the user is being removed from
   */
  def removeUser(userId: UUID, space: UUID)

  /**
   * Update a user's role within a space.
   *
   * @param userId The identifier of the user to be updated
   * @param role The new role to be assigned to the user in the space
   * @param space The identifier of the space to be updated
   *
   */
  def changeUserRole(userId: UUID, role: Role, space: UUID)


  /**
   * Update space.userCount if it is not correct.
   *
   * @param space The identifier of the space to be updated
   * @param numberOfUser The number of user in space
   *
   */
  def updateUserCount(space: UUID, numberOfUser:Int)

  /**
   * Retrieve the users that are associated with a specific space.
   *
   * @param spaceId The identifier of the space to retrieve user data from
   * @param role    The role of the user in the space (optional filter)
   *
   * @return A list that contains all of the users that are associated with a specific space
   *
   */
  def getUsersInSpace(spaceId: UUID, role: Option[String]): List[User]

  /**
   * Retrieve the role associated to a user for a given space.
   *
   * @param spaceId The identifier of the space to get data for
   * @param userId The identifier of the user to retrieve data for within the space
   *
   * @return The role that a specific user has within the specified space
   *
   */
  def getRoleForUserInSpace(spaceId: UUID, userId: UUID): Option[Role]

  /**
   * Add follower to a file.
   */
  def addFollower(id: UUID, userId: UUID)

  /**
   * Remove follower from a file.
   */
  def removeFollower(id: UUID, userId: UUID)

  /**
   * Add Invitation to a Space
   */
  def addInvitationToSpace(invite: SpaceInvite)

  /**
   * Remove Invitation to a space
   */
  def removeInvitationFromSpace(inviteId: UUID, spaceId: UUID)

  /**
   * Find an invitation by ID
   */
  def getInvitation(inviteId: String): Option[SpaceInvite]

  /**
   * Find invitations of a space. Get data from SpaceInviteDao.
   */
  def getInvitationBySpace(space: UUID): List[SpaceInvite]

  /**
   * Find invitations of a space. Get data from SpaceInviteDao.
   */
  def getInvitationByEmail(email: String): List[SpaceInvite]

  /**
    * Find invitation for email and process it
    */
  def processInvitation(email: String)

  /**
   * Add authorization request to a space.
   */
  def addRequest(id: UUID, userId: UUID, username: String)

  /**
   * Remove authorization request.
   */
  def removeRequest(id: UUID, userId: UUID)

  def addCurationObject(spaceId: UUID, curationObjectId: UUID)

  def removeCurationObject(spaceId: UUID, curationObjectId: UUID)

  /**
	 * If entry for spaceId already exists, will update list of extractors.
	 * Otherwise will create and add a new document to the collection, with spaceId and extractor given.
	 */
	def enableExtractor(spaceId: UUID, extractor: String)

  /**
   * Disable extractors within the space. This is used to override global selections.
   * @param spaceId
   * @param extractor
   */
  def disableExtractor(spaceId: UUID, extractor: String)


  /**
   * Follow the global setting for whether to trigger an extractor or not.
   * @param spaceId
   * @param extractor
   */
  def setDefaultExtractor(spaceId: UUID, extractor: String)
	
	/**
	 * Get all extractors for this space id. This is the union of all enabled and disabled extractors for this space.
   * If a user never manually enabled or disabled an extractor for a space it will not be returned, but the extractor
   * might still be enabled/disabled at the instance level.
	 */
  def getAllExtractors(spaceId: UUID): Option[ExtractorsForSpace]

  /**
	 * Delete an entire entry with extractors for this space id.
	 */
	def deleteAllExtractors(spaceId: UUID): Boolean
}