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
    user match {
      case Some(modeluser) => {
        Event.insert(new Event(modeluser.getMiniUser, Option(object_id), Option(object_name), None, None, action_type, new Date())) 
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

  def getAllEvents(followedEntities:List[TypedID]) = {
    /**
    var userEvents = getAllEventsOfType(usersFollowed, "user._id")

    var collections_objects = getAllEventsOfType(collectionsFollowed, "object_id")
    var collections_source = getAllEventsOfType(collectionsFollowed, "source_id")

    var datasets_objects = getAllEventsOfType(datasetsFollowed, "object_id")
    var datasets_source = getAllEventsOfType(datasetsFollowed, "source_id")

    var files_objects = getAllEventsOfType(filesFollowed, "object_id")
    var files_source = getAllEventsOfType(filesFollowed, "source_id")

    var collectionEvents = List.concat(collections_objects, collections_source)
    var datasetEvents = List.concat(datasets_objects, datasets_source)
    var fileEvents = List.concat(files_objects, files_source)

    var eventsList = List.concat(userEvents, collectionEvents)
    eventsList = List.concat(eventsList, datasetEvents)
    eventsList = List.concat(eventsList, fileEvents)
    */
    var followedIDs = (for (typedid <- followedEntities) yield typedid.id).toList

    var userEvents = getAllEventsOfType(followedIDs, "user._id")
    var objectsEvents = getAllEventsOfType(followedIDs, "object_id")
    var sourceEvents = getAllEventsOfType(followedIDs, "source_id")

    var eventsList = List.concat(userEvents, objectsEvents)
    eventsList = List.concat(eventsList, sourceEvents)

    eventsList = eventsList.sortBy(_.created)
    eventsList = eventsList.distinct
    eventsList
  }


  def getAllEventsOfType(following: List[UUID], id_type: String): List[Event] = {
    var listOfLists = (for (id <- following) yield (for (event <- getEvents(id.stringify, id_type)) yield event)).toList
    var eventList = List[models.Event]()
    for (list <- listOfLists) for (resultLine <- list) (eventList =   resultLine :: eventList)
    eventList
  }


  def getEvents(id: String, id_type: String) : SalatMongoCursor[Event] = {
    Event.find(MongoDBObject(id_type -> new ObjectId(id)))
  }

}

object Event extends ModelCompanion[Event, ObjectId] {
  val dao = current.plugin[MongoSalatPlugin] match {
    case None => throw new RuntimeException("No MongoSalatPlugin");
    case Some(x) => new SalatDAO[Event, ObjectId](collection = x.collection("events")) {}
  }
}
