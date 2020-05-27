package services.mongodb

import javax.inject.Inject

import api.Permission
import models._
import java.util.Date
import org.bson.types.ObjectId
import services._
import api.Permission.Permission
import com.novus.salat.dao.{ModelCompanion, SalatDAO}
import MongoContext.context
import play.api.Play.current
import play.Logger
import com.mongodb.casbah.commons.MongoDBObject
import com.mongodb.casbah.Imports._
import com.novus.salat.dao.SalatMongoCursor

/**
  * Use MongoDB for storing events.
 */
class MongoDBEventService @Inject() (
     userService: UserService,
     spaces:SpaceService,
     datasets: DatasetService,
     Folders:FolderService) extends EventService {


  def listEvents(): List[Event] = {
    (for (event <- Event.find(MongoDBObject())) yield event).toList
  }

  def addEvent(event: Event) = {
    Event.insert(event)
  }

  def addUserEvent(user: Option[User], action_type: String) = {
    user match {
      case Some(modeluser) => {
        Event.insert(new Event(modeluser.getMiniUser, None, None, None, None, None, action_type, new Date()))
      }
      case None => Logger.error("No user provided")
    }
  }

  def addObjectEvent(user: Option[User], object_id: UUID, object_name: String, action_type: String) = {
    if (object_name.toString() != "undefined"){
      user match {
        case Some(modeluser) => {
          Event.insert(new Event(modeluser.getMiniUser, None, Option(object_id), Option(object_name), None, None, action_type, new Date())) 
        }
        case None => Logger.error("No user provided")
      }
    }
  }

  def addSourceEvent(user: Option[User], object_id: UUID, object_name: String, source_id: UUID, source_name: String, action_type: String) = {
    user match {
      case Some(modeluser) => {
        Event.insert(new Event(modeluser.getMiniUser, None, Option(object_id), Option(object_name), Option(source_id), Option(source_name), action_type, new Date())) 
      }
      case None => Logger.error("No user provided")
    }
  }

  def addRequestEvent(user: Option[User], targetuser: User, object_id: UUID, object_name: String,  action_type: String) = {
    user match {
      case Some(modeluser) => {
        Event.insert(new Event(modeluser.getMiniUser, Option(targetuser.getMiniUser), Option(object_id), Option(object_name), None, None, action_type, new Date()))
      }
      case None => Logger.error("No user provided")
    }
  }


  def getEventsByTime(followedEntities:List[TypedID], time: Date, limit: Option[Integer]) = {

    var followedIDs = (for (typedid <- followedEntities) yield typedid.id).toList

    var userEvents = getEventsOfType(followedIDs, "user._id", limit)
    var objectsEvents = getEventsOfType(followedIDs, "object_id", limit)
    var sourceEvents = getEventsOfType(followedIDs, "source_id", limit)

    var eventsList = List.concat(userEvents, objectsEvents)
    eventsList = List.concat(eventsList, sourceEvents)

    eventsList = eventsList.sortBy(_.created).reverse
    eventsList = eventsList.distinct
    eventsList = eventsList.filter(_.created.after(time))

    limit match {
      case Some(x) => eventsList.take(x)
      case None => eventsList
    }
  }

  def getEvents(followedEntities:List[TypedID], limit: Option[Integer]) = {

    var followedIDs = (for (typedid <- followedEntities) yield typedid.id).toList

    var userEvents = getEventsOfType(followedIDs, "user._id", limit)
    var objectsEvents = getEventsOfType(followedIDs, "object_id", limit)
    var sourceEvents = getEventsOfType(followedIDs, "source_id", limit)

    var eventsList = List.concat(userEvents, objectsEvents)
    eventsList = List.concat(eventsList, sourceEvents)

    eventsList = eventsList.sortBy(_.created).reverse
    eventsList = eventsList.distinct

    limit match {
      case Some(x) => eventsList.take(x)
      case None => eventsList
    }
  }


  def getEventsOfType(following: List[UUID], id_type: String, limit: Option[Integer]): List[Event] = {
    var listOfLists = (for (id <- following) yield (for (event <- getEvents(id.stringify, id_type, limit)) yield event)).toList
    var eventList = List[models.Event]()
    for (list <- listOfLists) for (resultLine <- list) (eventList =   resultLine :: eventList)

    limit match {
      case Some(x) => eventList.take(x)
      case None => eventList
    }
  }


  def getEvents(id: String, id_type: String, limit: Option[Integer]) : SalatMongoCursor[Event] = {
    val result = Event.find(MongoDBObject(id_type -> new ObjectId(id)))

    limit match {
      case Some(x) => result.limit(x)
      case None => result
    }
  }

  def getLatestNEventsOfType(n: Int, event_type: Option[String]): List[Event] = {
    event_type match {
      case Some(type_to_search) => {
        Event.find(
          MongoDBObject(
            "event_type" -> (".*" + type_to_search + ".*").r
          )
        ).sort(MongoDBObject("created" -> -1)).limit(n).toList
      }
      case None => {
        Event.find(MongoDBObject()).sort(MongoDBObject("created" -> -1)).limit(n).toList
      }
    }
  }


   def getRequestEvents(targetuser: Option[User], limit: Option[Integer]): List[Event] = {
     targetuser match {
       case Some(modeluser) => {
         val eventList = Event.find(
           MongoDBObject(
             "targetuser._id" -> new ObjectId(modeluser.id.stringify)
           )
         ).toList

           Logger.debug("find " + eventList.size + " request")
          limit match {
            case Some(x) => eventList.take(x)
            case None => eventList
          }
        }
       case None => List()
     }
   }

  def getEventsByUser( user: User, limit: Option[Integer]): List[Event] ={
    val eventList = (Event.find(MongoDBObject(
      "user._id"-> new ObjectId(user.id.toString()))).toList  :::
      Event.find(MongoDBObject(
      "object_id"-> new ObjectId(user.id.toString()))).toList)
        .distinct.sorted(Ordering.by((_: Event).created).reverse)

    limit match {
      case Some(x) => eventList.take(x)
      case None => eventList
    }
  }

  def getCommentEvent( user: User, limit: Option[Integer]): List[Event] ={
    val roleList = userService.listRoles().filter(_.permissions.contains(Permission.ViewComments.toString))
    val spaceIdList = user.spaceandrole.filter(x => roleList.contains(x.role)).map(_.spaceId)
    val datasetList = (datasets.listUser(0,  None, true, user) ::: spaceIdList.map(s => datasets.listSpace(0, s.toString)).flatten).distinct
    val fileIdList = (datasetList.map(_.files) ::: datasetList.map(d => Folders.findByParentDatasetId(d.id).map(_.files).flatten)).flatten
    val eventList = (Event.find(MongoDBObject(
        "event_type" -> "add_comment_dataset") ++
        ("source_id" $in datasetList.map(x => new ObjectId(x.id.stringify)))
    ).toList:::
      Event.find(MongoDBObject(
        "event_type" -> "comment_file") ++
        ("source_id" $in fileIdList.map(x => new ObjectId(x.stringify)))).toList)
      .distinct.sorted(Ordering.by((_: Event).created).reverse)

    limit match {
      case Some(x) => eventList.take(x)
      case None => eventList
    }
  }

  def updateObjectName(id:UUID, name:String) = {
    Event.dao.update(MongoDBObject("object_id" -> new ObjectId(id.stringify)), $set("object_name" -> name), multi = true)
    Event.dao.update(MongoDBObject("source_id" -> new ObjectId(id.stringify)), $set("source_name" -> name), multi = true)
  }

  def updateAuthorFullName(userId: UUID, fullName: String) {
    Event.update(MongoDBObject("user._id" -> new ObjectId(userId.stringify)),
      $set("user.fullName" -> fullName), false, true, WriteConcern.Safe)
    Event.update(MongoDBObject("targetuser._id" -> new ObjectId(userId.stringify)),
      $set("targetuser.fullName" -> fullName), false, true, WriteConcern.Safe)
  }

}

object Event extends ModelCompanion[Event, ObjectId] {
  val dao = current.plugin[MongoSalatPlugin] match {
    case None => throw new RuntimeException("No MongoSalatPlugin");
    case Some(x) => new SalatDAO[Event, ObjectId](collection = x.collection("events")) {}
  }
}
