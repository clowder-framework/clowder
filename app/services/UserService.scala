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


  def editField(email: String, field: String, fieldText: Any)

  def editList(email: String, field: String, fieldList: Any)
  def createList(email: String, field: String, fieldList: List[Any])
}
