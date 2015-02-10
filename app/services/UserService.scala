package services

import models.{UUID, User}
import securesocial.core.Identity
import util.Direction._

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
trait UserService  {
  def get(id: UUID): Option[User]

  def insert(model: User): Option[String]

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
  def list(order: Option[String] = None, direction: Direction=DESC,
           start: Option[String] = None, limit: Integer = 20,
           filter: Option[String] = None): List[User]

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
}
