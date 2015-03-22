package services.mongodb

import models.Event
import models.UUID
import models.User
import java.util.Date
import services.EventService
import com.novus.salat.dao.{ModelCompanion, SalatDAO}
import MongoContext.context
import play.api.Play.current
import play.Logger
import com.mongodb.casbah.commons.MongoDBObject
import com.mongodb.casbah.Imports._

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

  def getAllEvents(usersFollowed: List[UUID], collectionsFollowed: List[UUID], datasetsFollowed: List[UUID], filesFollowed: List[UUID]) = {
    var userEvents = getAllUserEvents(usersFollowed)
    var collections_objects = getAllObjectEvents(collectionsFollowed)
    var collections_source = getAllSourceEvents(collectionsFollowed)
    var datasets_objects = getAllObjectEvents(datasetsFollowed)
    var datasets_source = getAllSourceEvents(datasetsFollowed)
    var files_objects = getAllObjectEvents(filesFollowed)
    var files_source = getAllSourceEvents(filesFollowed)

    var collectionEvents = List.concat(collections_objects, collections_source)
    var datasetEvents = List.concat(datasets_objects, datasets_source)
    var fileEvents = List.concat(files_objects, files_source)

    var eventsList = List.concat(userEvents, collectionEvents)
    eventsList = List.concat(eventsList, datasetEvents)
    eventsList = List.concat(eventsList, fileEvents)
    
    eventsList = eventsList.sortBy(_.created)
    eventsList
  }

  def getAllUserEvents(usersFollowed: List[UUID]) = {
    var listOfLists = getUserEvents(usersFollowed)
    var eventList = List[models.Event]()
    for (list <- listOfLists) for (resultLine <- list) (eventList =   resultLine :: eventList)
    eventList
  }

  def getAllObjectEvents(objectFollowers: List[UUID]) = {
    var listOfLists = getObjectEvents(objectFollowers)
    var eventList = List[models.Event]()
    for (list <- listOfLists) for (resultLine <- list) (eventList =   resultLine :: eventList)
    eventList
  }

  def getAllSourceEvents(sourceFollowers: List[UUID]) = {
    var listOfLists = getSouceEvents(sourceFollowers)
    var eventList = List[models.Event]()
    for (list <- listOfLists) for (resultLine <- list) (eventList =   resultLine :: eventList)
    eventList
  }

  def getUserEvents(usersFollowed: List[UUID]) = {
    (for (id <- usersFollowed) yield (for (event <- getEventsForUser(id.stringify)) yield event)).toList
  }

  def getObjectEvents(objectFollowers: List[UUID]) = {
    (for (id <- objectFollowers) yield (for (event <- getEventsForObject(id.stringify)) yield event)).toList
  }

  def getSouceEvents(sourceFollowers: List[UUID]) = {
    (for (id <- sourceFollowers) yield (for (event <- getEventsForSource(id.stringify)) yield event)).toList
  }

  def getEventsForUser(id: String) = {
    Event.find(MongoDBObject("user._id" -> new ObjectId(id)))
  }

  def getEventsForObject(id: String) = {
    Event.find(MongoDBObject("object_id" -> new ObjectId(id)))
  }

  def getEventsForSource(id: String) = {
    Event.find(MongoDBObject("source_id" -> new ObjectId(id)))
  }
}

object Event extends ModelCompanion[Event, ObjectId] {
  val dao = current.plugin[MongoSalatPlugin] match {
    case None => throw new RuntimeException("No MongoSalatPlugin");
    case Some(x) => new SalatDAO[Event, ObjectId](collection = x.collection("events")) {}
  }
}
