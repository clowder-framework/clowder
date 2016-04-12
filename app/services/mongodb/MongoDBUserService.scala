package services.mongodb

import com.mongodb.casbah.WriteConcern
import java.util.Date
import com.mongodb.DBObject
import com.mongodb.util.JSON
import com.novus.salat._
import com.novus.salat.dao.{ModelCompanion, SalatDAO}
import models._
import org.bson.types.ObjectId
import securesocial.core.Identity
import play.api.Application
import play.api.Play.current
import com.mongodb.casbah.commons.MongoDBObject
import com.mongodb.casbah.Imports._
import models.Role
import models.UserSpaceAndRole
import scala.collection.mutable.ListBuffer
import play.api.Logger
import securesocial.core.providers.Token
import securesocial.core._
import services._
import services.mongodb.MongoContext.context
import _root_.util.Direction._
import javax.inject.Inject

/**
 * Wrapper around SecureSocial to get access to the users. There is
 * no save option since all saves should be done through securesocial
 * right now. Eventually this should become a wrapper for
 * securesocial and we use User everywhere.
 *
 *
 */
class MongoDBUserService @Inject() (
  files: FileService,
  datasets: DatasetService,
  collections: CollectionService,
  spaces: SpaceService,
  comments: CommentService,
  curations: CurationService) extends services.UserService {
  // ----------------------------------------------------------------------
  // Code to implement the common CRUD services
  // ----------------------------------------------------------------------

  override def update(model: User): Unit = insert(model: User)

  override def insert(model: User): Option[User] = {
    val query = MongoDBObject("identityId.userId" -> model.identityId.userId, "identityId.providerId" -> model.identityId.providerId)
    val user = UserDAO.toDBObject(model)
    // If account does not exist, add enabled option
    if (UserDAO.count(query) == 0) {
      val register = play.Play.application().configuration().getBoolean("registerThroughAdmins", true)
      val admins = play.Play.application().configuration().getString("initialAdmins").split("\\s*,\\s*")
      // enable account. Admins are always enabled.
      model.email match {
        case Some(e) if admins.contains(e) => {
          user.put("active", true)
          user.put("admin", true)
        }
        case _ => {
          user.put("active", !register)
          user.put("admin", false)
        }
      }
    } else {
      user.removeField("_id")
    }
    UserDAO.update(query, MongoDBObject("$set" -> user), upsert = true, multi = false, WriteConcern.Safe)
    UserDAO.findOne(query)
  }

  override def get(id: UUID): Option[User] = {
    if (id == User.anonymous.id)
      Some(User.anonymous)
    else
      UserDAO.findOneById(new ObjectId(id.stringify))
  }

  override def delete(id: UUID): Unit = {
    UserDAO.remove(MongoDBObject("id" -> id))
  }

  override def updateAdmins() {
    play.Play.application().configuration().getString("initialAdmins").trim.split("\\s*,\\s*").filter(_ != "").foreach{e =>
      UserDAO.dao.update(MongoDBObject("email" -> e), $set("admin" -> true, "active" -> true), upsert=false, multi=true)
    }
  }

  override def getAdmins: List[User] = {
    UserDAO.find(MongoDBObject("admin" -> true, "active" -> true)).toList
  }

  /**
   * The number of objects that are available based on the filter
   */
  override def count(filter: Option[String]): Long = {
    val filterBy = filter.fold(MongoDBObject())(JSON.parse(_).asInstanceOf[DBObject])
    UserDAO.count(filterBy)
  }

  /**
   * Return a list objects that are available based on the filter as well as the other options.
   *
   * @param order the key to use to order the data, default is natural ordering of underlying implementation
   * @param direction the direction to order the data in
   * @param start the first element that should be returned based on the order key
   * @param limit the maximum number of elements to return
   * @param filter is a json representation of the filter to be applied
   */
  override def list(order: Option[String], direction: Direction, start: Option[String], limit: Integer,
                    filter: Option[String]): List[User] = {
    val startAt = (order, start) match {
      case (Some(o), Some(s)) => {
        direction match {
          case ASC => (o $gte s)
          case DESC => (o $lte s)
        }
      }
      case (_, _) => MongoDBObject()
    }
    // what happens if we sort by user, and a user has uploaded 100 items?
    // how do we know that we need to show page 3 of that user?
    // TODO always sort by date ascending, start is based on user/start combo
    val filterBy = filter.fold(MongoDBObject())(JSON.parse(_).asInstanceOf[DBObject])
    val raw = UserDAO.find(startAt ++ filterBy)
    val orderedBy = order match {
      case Some(o) => {
        direction match {
          case ASC => raw.sort(MongoDBObject(o -> 1))
          case DESC => raw.sort(MongoDBObject(o -> -1))
        }
      }
      case None => raw
    }
    orderedBy.limit(limit).toList
  }

  override def list(id: Option[String], nextPage: Boolean, limit: Integer): List[User] = {
    val filterDate = id match {
      case Some(d) => {
        if(d == "") {
          MongoDBObject()
        } else if (nextPage) {
          ("_id" $lt new ObjectId(d))
        } else {
          ("_id" $gt new ObjectId(d))
        }
      }
      case None => MongoDBObject()
    }
    val sort = if (id.isDefined && !nextPage) {
      MongoDBObject("_id"-> 1) ++ MongoDBObject("name" -> 1)
    } else {
      MongoDBObject("_id" -> -1) ++ MongoDBObject("name" -> 1)
    }
    if(id.isEmpty || nextPage) {
      UserDAO.find(filterDate).sort(sort).limit(limit).toList
    } else {
      UserDAO.find(filterDate).sort(sort).limit(limit).toList.reverse
    }

  }

  // ----------------------------------------------------------------------
  // Code implementing specific functions
  // ----------------------------------------------------------------------
  /**
   * Return a specific user based on the id provided.
   */
  override def findById(id: UUID): Option[User] = {
    get(id)
  }

  /**
   * Return a specific user based on an Identity
   */
  override def findByIdentity(identity: Identity): Option[User] = {
    if (User.anonymous == identity)
      return Some(User.anonymous)
    else
      UserDAO.dao.findOne(MongoDBObject("identityId.userId" -> identity.identityId.userId, "identityId.providerId" -> identity.identityId.providerId))
  }

  /**
   * Return a specific user based on an Identity
   */
  override def findByIdentity(userId: String, providerId: String): Option[User] = {
    if (User.anonymous.identityId.userId == userId && User.anonymous.identityId.providerId == providerId)
      return Some(User.anonymous)
    else
      UserDAO.dao.findOne(MongoDBObject("identityId.userId" -> userId, "identityId.providerId" -> providerId))
  }

  /**
   * Return a specific user based on the email provided.
   */
  override def findByEmail(email: String): Option[User] = {
    UserDAO.dao.findOne(MongoDBObject("email" -> email))
  }

  override def updateProfile(id: UUID, profile: Profile) {
    val pson = grater[Profile].asDBObject(profile)
    UserDAO.dao.update(MongoDBObject("_id" -> new ObjectId(id.stringify)), $set("profile" -> pson))
  }

  override def updateUserField(email: String, field: String, fieldText: Any) {
    UserDAO.dao.update(MongoDBObject("email" -> email), $set(field -> fieldText))
  }

  override def updateUserFullName(id: UUID, name: String): Unit = {
    datasets.updateAuthorFullName(id, name)
    collections.updateAuthorFullName(id, name)
    files.updateAuthorFullName(id, name)
    curations.updateAuthorFullName(id, name)
    comments.updateAuthorFullName(id, name)

  }

  override def addUserDatasetView(email: String, dataset: UUID) {
    UserDAO.dao.update(MongoDBObject("email" -> email), $push("viewed" -> dataset))
  }

  override def createNewListInUser(email: String, field: String, fieldList: List[Any]) {
    UserDAO.dao.update(MongoDBObject("email" -> email), $set(field -> fieldList))
  }

  override def updateRepositoryPreferences(id: UUID, preferences: Map[String, String])  {
    UserDAO.dao.update(MongoDBObject("_id" -> new ObjectId(id.stringify)), $set("repositoryPreferences" -> preferences))
  }
  /**
   * @see app.services.UserService
   * 
   * Implementation of the UserService trait.
   * 
   */
  def addUserToSpace(userId: UUID, role: Role, spaceId: UUID): Unit = {
      Logger.debug("add user to space")
      val spaceData = UserSpaceAndRole(spaceId, role)
      val result = UserDAO.dao.update(MongoDBObject("_id" -> new ObjectId(userId.stringify)), $push("spaceandrole" -> UserSpaceAndRoleData.toDBObject(spaceData)));  
  }
 
  /**
   * @see app.services.UserService
   * 
   * Implementation of the UserService trait.
   * 
   */
  def removeUserFromSpace(userId: UUID, spaceId: UUID): Unit = {
      Logger.debug("remove user from space")
      UserDAO.dao.update(MongoDBObject("_id" -> new ObjectId(userId.stringify)),
    		  $pull("spaceandrole" ->  MongoDBObject( "spaceId" -> new ObjectId(spaceId.stringify))), false, false, WriteConcern.Safe)
  }
  
  /**
   * @see app.services.UserService
   * 
   * Implementation of the UserService trait.
   * 
   */
  def changeUserRoleInSpace(userId: UUID, role: Role, spaceId: UUID): Unit = {
    UserDAO.dao.update(MongoDBObject("_id" -> new ObjectId(userId.stringify), "spaceandrole.spaceId" -> new ObjectId(spaceId.stringify)),
        $set({"spaceandrole.$.role" -> RoleDAO.toDBObject(role)}), false, true, WriteConcern.Safe)
  }
  
  /**
   * @see app.services.UserService
   * 
   * Implementation of the UserService trait.
   * 
   */
  def getUserRoleInSpace(userId: UUID, spaceId: UUID): Option[Role] = {
      var retRole: Option[Role] = None
      var found = false
      
      findById(userId) match {
          case Some(aUser) => {              
              for (aSpaceAndRole <- aUser.spaceandrole) {                  
                  if (!found) {
                      if (aSpaceAndRole.spaceId == spaceId) {
                          retRole = Some(aSpaceAndRole.role)
                          found = true
                      }	                  
                  }
              }
          }
          case None => Logger.debug("No user found for getRoleInSpace")
      }
      
      retRole
  }
  
  /**
   * @see app.services.UserService
   * 
   * Implementation of the UserService trait.
   * 
   */
  def listUsersInSpace(spaceId: UUID): List[User] = {
      val retList: ListBuffer[User] = ListBuffer.empty
      for (aUser <- UserDAO.dao.find(MongoDBObject())) {
         for (aSpaceAndRole <- aUser.spaceandrole) {
             if (aSpaceAndRole.spaceId == spaceId) {
                 retList += aUser
             }             
         }
      }      
      retList.toList
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

  def findRoleByName(name: String): Option[Role] = {
    RoleDAO.findByName(name)
  }
  /**
   * Delete role.
   */
  def deleteRole(id: String): Unit = {
    RoleDAO.removeById(id)

    // Stored role data in the users table must also be deleted
    // Get only list of users with the updated Role in one of their spaces so we don't fetch them all
    UserDAO.dao.collection.find(MongoDBObject("spaceandrole.role._id" -> new ObjectId(id))).foreach { u =>
      val userid: UUID = u.get("_id") match {
        case i: ObjectId => UUID(i.toString)
        case i: UUID => i
        case None => UUID("")
      }

      // Get list of space+role combination objects for this user
      u.get("spaceandrole") match {
        case sp_roles: BasicDBList => {
          for (sp_role <- sp_roles) {
            sp_role match {
              case s: BasicDBObject => {
                val spaceid: UUID = s.get("spaceId") match {
                  case i: ObjectId => UUID(i.toString)
                  case i: UUID => i
                  case None => UUID("")
                }

                // For each one, check whether this role is the changed one and change if so
                s.get("role") match {
                  case r: BasicDBObject => {
                    val roleid: String = r.get("_id") match {
                      case i: ObjectId => i.toString
                      case i: UUID => i.toString
                      case None => ""
                    }

                    if (roleid == id) {
                      removeUserFromSpace(userid, spaceid)
                    }

                  }
                  case None => {}
                }
              }
              case None => {}
            }
          }
        }
        case None => {}
      }
    }
  }

  def updateRole(role: Role): Unit = {
    RoleDAO.save(role)

    // Stored role data in the users table must also be updated
    // Get only list of users with the updated Role in one of their spaces so we don't fetch them all
    UserDAO.dao.collection.find(MongoDBObject("spaceandrole.role._id" -> new ObjectId(role.id.stringify))).foreach { u =>
      val userid: UUID = u.get("_id") match {
        case i: ObjectId => UUID(i.toString)
        case i: UUID => i
        case None => UUID("")
      }

      // Get list of space+role combination objects for this user
      u.get("spaceandrole") match {
        case sp_roles: BasicDBList => {
          for (sp_role <- sp_roles) {
            sp_role match {
              case s: BasicDBObject => {
                val spaceid: UUID = s.get("spaceId") match {
                  case i: ObjectId => UUID(i.toString)
                  case i: UUID => i
                  case None => UUID("")
                }

                // For each one, check whether this role is the changed one and change if so
                s.get("role") match {
                  case r: BasicDBObject => {
                    val roleid: UUID = r.get("_id") match {
                      case i: ObjectId => UUID(i.toString)
                      case i: UUID => i
                      case None => UUID("")
                    }

                    if (roleid == role.id)
                      changeUserRoleInSpace(userid, role, spaceid)
                  }
                  case None => {}
                }
              }
              case None => {}
            }
          }
        }
        case None => {}
      }
    }
  }


  override def followResource(followerId: UUID, resourceRef: ResourceRef) {
    UserDAO.dao.update(MongoDBObject("_id" -> new ObjectId(followerId.stringify)),
      $addToSet("followedEntities" -> TypedIDDAO.toDBObject(new TypedID(resourceRef.id, resourceRef.resourceType.toString()))))
  }

  override def unfollowResource(followerId: UUID, resourceRef: ResourceRef) {
    UserDAO.dao.update(MongoDBObject("_id" -> new ObjectId(followerId.stringify)),
      $pull("followedEntities" -> TypedIDDAO.toDBObject(new TypedID(resourceRef.id, resourceRef.resourceType.toString()))))
  }

  override def followFile(followerId: UUID, fileId: UUID) {
    UserDAO.dao.update(MongoDBObject("_id" -> new ObjectId(followerId.stringify)),
                        $addToSet("followedEntities" -> TypedIDDAO.toDBObject(new TypedID(fileId, "file"))))
  }

  override def unfollowFile(followerId: UUID, fileId: UUID) {
    UserDAO.dao.update(MongoDBObject("_id" -> new ObjectId(followerId.stringify)),
                        $pull("followedEntities" -> TypedIDDAO.toDBObject(new TypedID(fileId, "file"))))
  }

  override def followDataset(followerId: UUID, datasetId: UUID) {
    UserDAO.dao.update(MongoDBObject("_id" -> new ObjectId(followerId.stringify)),
                        $addToSet("followedEntities" -> TypedIDDAO.toDBObject(new TypedID(datasetId, "dataset"))))
  }

  override def unfollowDataset(followerId: UUID, datasetId: UUID) {
    UserDAO.dao.update(MongoDBObject("_id" -> new ObjectId(followerId.stringify)),
                        $pull("followedEntities" -> TypedIDDAO.toDBObject(new TypedID(datasetId, "dataset"))))
  }

  /**
   * Follow a collection.
   */
  override def followCollection(followerId: UUID, collectionId: UUID) {
    UserDAO.dao.update(MongoDBObject("_id" -> new ObjectId(followerId.stringify)),
                        $addToSet("followedEntities" -> TypedIDDAO.toDBObject(new TypedID(collectionId, "collection"))))
  }

  /**
   * Unfollow a collection.
   */
  override def unfollowCollection(followerId: UUID, collectionId: UUID) {
    UserDAO.dao.update(MongoDBObject("_id" -> new ObjectId(followerId.stringify)),
                        $pull("followedEntities" -> TypedIDDAO.toDBObject(new TypedID(collectionId, "collection"))))
  }

  /**
   * Follow a user.
   */
  override def followUser(followeeId: UUID, followerId: UUID)
  {
    UserDAO.dao.update(MongoDBObject("_id" -> new ObjectId(followerId.stringify)),
                        $addToSet("followedEntities" -> TypedIDDAO.toDBObject(new TypedID(followeeId, "user"))))
    UserDAO.dao.update(MongoDBObject("_id" -> new ObjectId(followeeId.stringify)),
                        $addToSet("followers" -> new ObjectId(followerId.stringify)))
  }

  /**
   * Unfollow a user.
   */
  override def unfollowUser(followeeId: UUID, followerId: UUID) {
    UserDAO.dao.update(MongoDBObject("_id" -> new ObjectId(followerId.stringify)),
                        $pull("followedEntities" -> TypedIDDAO.toDBObject(new TypedID(followeeId, "user"))))
    UserDAO.dao.update(MongoDBObject("_id" -> new ObjectId(followeeId.stringify)),
                        $pull("followers" -> new ObjectId(followerId.stringify)))
  }

  /**
   * return List of tuples {id, objectType, score}
   *   representing the top N recommendations for an object with followerIDs
   *   This list will also filter out excludeIDs (i.e. items the logged in user already follows)
   */
  override def getTopRecommendations(followerIDs: List[UUID], excludeIDs: List[UUID], num: Int): List[MiniEntity] = {
    val followerIDObjects = followerIDs.map(id => new ObjectId(id.stringify))
    val excludeIDObjects = excludeIDs.map(id => new ObjectId(id.stringify))

    val recs = UserDAO.dao.collection.aggregate(
        MongoDBObject("$match" -> MongoDBObject("_id" -> MongoDBObject("$in" -> followerIDObjects))),
        MongoDBObject("$unwind" -> "$followedEntities"),
        MongoDBObject("$group" -> MongoDBObject(
          "_id" -> "$followedEntities._id",
          "objectType" -> MongoDBObject("$first" -> "$followedEntities.objectType"),
          "score" -> MongoDBObject("$sum" -> 1)
        )),
        MongoDBObject("$match" -> MongoDBObject("_id" -> MongoDBObject("$nin" -> excludeIDObjects))),
        MongoDBObject("$sort" -> MongoDBObject("score" -> -1)),
        MongoDBObject("$limit" -> num)
    )

    recs.results.map(entity => new MiniEntity(
      UUID(entity.as[ObjectId]("_id").toString),
      getEntityName(
        UUID(entity.as[ObjectId]("_id").toString),
        entity.as[String]("objectType")
      ),
      entity.as[String]("objectType"))
    ).toList
  }

  def getEntityName(uuid: UUID, objType: String): String = {
    val default = "Not found"
    objType match {
      case "user" => {
        get(uuid) match {
          case Some(user) => user.fullName
          case None => default
        }
      }
      case "file" => {
        files.get(uuid) match {
          case Some(file) => file.filename
          case None => default
        }
      }
      case "dataset" => {
        datasets.get(uuid) match {
          case Some(dataset) => dataset.name
          case None => default
        }
      }
      case "collection" => {
        collections.get(uuid) match {
          case Some(collection) => collection.name
          case None => default
        }
      }
      case "'space" => {
        spaces.get(uuid) match {
          case Some(space) => space.name
          case None => default
        }
      }
      case _ => default
    }
  }

  object UserDAO extends ModelCompanion[User, ObjectId] {
    val dao = current.plugin[MongoSalatPlugin] match {
      case None => throw new RuntimeException("No MongoSalatPlugin");
      case Some(x) => new SalatDAO[User, ObjectId](collection = x.collection("social.users")) {}
    }
  }
}

class MongoDBSecureSocialUserService(application: Application) extends UserServicePlugin(application) {
  override def find(id: IdentityId): Option[User] = {
    UserDAO.dao.findOne(MongoDBObject("identityId.userId" -> id.userId, "identityId.providerId" -> id.providerId))
  }

  override def findByEmailAndProvider(email: String, providerId: String): Option[User] = {
    UserDAO.dao.findOne(MongoDBObject("email" -> email, "identityId.providerId" -> providerId))
  }

  override def save(user: Identity): User = {
    // user is always of type SocialUser when this function is entered
    // first convert the socialuser object to a mongodbobject
    val userobj = com.novus.salat.grater[Identity].asDBObject(user)

    // replace _typeHint with the right model type so it will get correctly deserialized
    userobj.put("_typeHint", "models.ClowderUser")

    // query to find the user based on identityId
    val query = MongoDBObject("identityId.userId" -> user.identityId.userId, "identityId.providerId" -> user.identityId.providerId)

    // If account does not exist, add enabled option
    if (UserDAO.count(query) == 0) {
      val register = play.Play.application().configuration().getBoolean("registerThroughAdmins", true)
      val admins = play.Play.application().configuration().getString("initialAdmins").split("\\s*,\\s*")
      // enable account. Admins are always enabled.
      user.email match {
        case Some(e) if admins.contains(e) => {
          userobj.put("active", true)
          userobj.put("admin", true)
        }
        case _ => {
          userobj.put("active", !register)
          userobj.put("admin", false)
        }
      }
    }

    // update all fields from past in user object
    val dbobj = MongoDBObject("$set" -> userobj)

    // update, if it does not exist do an insert (upsert = true)
    UserDAO.update(query, dbobj, upsert = true, multi = false, WriteConcern.Safe)

    // send email to admins new user is created

    // return the user object
    find(user.identityId).get
  }

  // ----------------------------------------------------------------------
  // Code to deal with tokens
  // ----------------------------------------------------------------------
  override def deleteToken(uuid: String): Unit = {
    TokenDAO.remove(MongoDBObject("uuid" -> uuid))
  }

  override def save(token: Token): Unit = {
    TokenDAO.save(token)
  }

  override def deleteExpiredTokens(): Unit = {
    TokenDAO.remove("expirationTime" $lt new Date)
    val invites = SpaceInviteDAO.find("expirationTime" $lt new Date)
    for(inv <- invites) {
      ProjectSpaceDAO.update(MongoDBObject("_id" -> new ObjectId(inv.space.stringify)),
        $pull("invitations" -> MongoDBObject( "_id" -> new ObjectId(inv.id.stringify))), upsert=false, multi=false, WriteConcern.Safe)
    }
    SpaceInviteDAO.remove("expirationTime" $lt new Date)
  }

  override def findToken(token: String): Option[Token] = {
    TokenDAO.findOne(MongoDBObject("uuid" -> token))
  }

  object TokenDAO extends ModelCompanion[Token, ObjectId] {
    val dao = current.plugin[MongoSalatPlugin] match {
      case None => throw new RuntimeException("No MongoSalatPlugin");
      case Some(x) => new SalatDAO[Token, ObjectId](collection = x.collection("social.token")) {}
    }
  }

  object UserDAO extends ModelCompanion[User, ObjectId] {
    val dao = current.plugin[MongoSalatPlugin] match {
      case None => throw new RuntimeException("No MongoSalatPlugin");
      case Some(x) => new SalatDAO[User, ObjectId](collection = x.collection("social.users")) {}
    }
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

  def findByName(name: String): Option[Role] = {
    dao.findOne(MongoDBObject("name" -> name))
  }
}

/**
 * ModelCompanion object for the models.UserSpaceAndRole class. Specific to MongoDB implementation, so should either
 * be in it's own utility class within services, or, as it is currently implemented, within one of the common
 * services classes that utilize it.
 */
object UserSpaceAndRoleData extends ModelCompanion[UserSpaceAndRole, ObjectId] {
  val dao = current.plugin[MongoSalatPlugin] match {
    case None => throw new RuntimeException("No MongoSalatPlugin");
    case Some(x) => new SalatDAO[UserSpaceAndRole, ObjectId](collection = x.collection("spaceandrole")) {}
  }
}

/**
  * Used to store Mini users in MongoDB.
  */
object MiniUserDAO extends ModelCompanion[MiniUser, ObjectId] {
  val dao = current.plugin[MongoSalatPlugin] match {
    case None => throw new RuntimeException("No MongoSalatPlugin");
    case Some(x) => new SalatDAO[MiniUser, ObjectId](collection = x.collection("social.miniusers")) {}
  }
}


