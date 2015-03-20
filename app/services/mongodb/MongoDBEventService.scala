package services.mongodb

import models.Event
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

}


object Event extends ModelCompanion[Event, ObjectId] {
  val dao = current.plugin[MongoSalatPlugin] match {
    case None => throw new RuntimeException("No MongoSalatPlugin");
    case Some(x) => new SalatDAO[Event, ObjectId](collection = x.collection("events")) {}
  }
}
