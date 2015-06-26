package services.mongodb

import models._
import java.util.Date
import services.EventService
import com.novus.salat.dao.{ModelCompanion, SalatDAO}
import MongoContext.context
import play.api.Play.current
import play.Logger
import com.mongodb.casbah.commons.MongoDBObject
import com.mongodb.casbah.Imports._
import com.novus.salat.dao.SalatMongoCursor

/**
 * @author Varun Kethineedi
 */
class MongoDBEventService extends EventService {


  def listEvents(): List[Event] = {
    (for (event <- Event.find(MongoDBObject())) yield event).toList
  }

  def addEvent(event: Event) = {
    Event.insert(event);
  }

  def addUserEvent(user: Option[User], action_type: String) = {
    user match {
      case Some(modeluser) => {
        Event.insert(new Event(modeluser.getMiniUser, None, None, None, None, action_type, new Date()))
      }
    }
  }

  def addObjectEvent(user: Option[User], object_id: UUID, object_name: String, action_type: String) = {
    if (object_name.toString() != "undefined"){
      user match {
        case Some(modeluser) => {
          Event.insert(new Event(modeluser.getMiniUser, Option(object_id), Option(object_name), None, None, action_type, new Date())) 
        }
      }
    }
  }

  def addSourceEvent(user: Option[User], object_id: UUID, object_name: String, source_id: UUID, source_name: String, action_type: String) = {
    user match {
      case Some(modeluser) => {
        Event.insert(new Event(modeluser.getMiniUser, Option(object_id), Option(object_name), Option(source_id), Option(source_name), action_type, new Date())) 
      }
    }
  }


  def getEventsByTime(followedEntities:List[TypedID], time: Date, limit: Option[Integer]) = {

    var followedIDs = (for (typedid <- followedEntities) yield typedid.id).toList

    var userEvents = getEventsOfType(followedIDs, "user._id", limit)
    var objectsEvents = getEventsOfType(followedIDs, "object_id", limit)
    var sourceEvents = getEventsOfType(followedIDs, "source_id", limit)

    var eventsList = List.concat(userEvents, objectsEvents)
    eventsList = List.concat(eventsList, sourceEvents)

    eventsList = eventsList.sortBy(_.created)
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

    eventsList = eventsList.sortBy(_.created)
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

}

object Event extends ModelCompanion[Event, ObjectId] {
  val dao = current.plugin[MongoSalatPlugin] match {
    case None => throw new RuntimeException("No MongoSalatPlugin");
    case Some(x) => new SalatDAO[Event, ObjectId](collection = x.collection("events")) {}
  }
}
