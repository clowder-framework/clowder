package services
import models._
import play.api.libs.json.JsValue
import com.mongodb.casbah.Imports._

import com.novus.salat.dao.SalatMongoCursor
/**
 * Service definition to interact with Events database.
 *
 * @author Varun Kethineedi
 */
trait EventService {
 
 /**
 * Lists all the events
 */

 def listEvents(): List[Event]

 /**
 * Adds a general event
 */

 def addEvent(event: Event)


 /**
 * Event where only the user is involved
 */
 def addUserEvent(user: Option[User], action_type: String)

 /**
 * Event where user interacts with one object
 */
 def addObjectEvent(user: Option[User], object_id: UUID, object_name: String, action_type: String)

 /**
 * Event where the user interacts with 2 objects, etc moving a dataset to a collection
 */
 def addSourceEvent(user: Option[User], object_id: UUID, object_name: String, source_id: UUID, source_name: String, action_type: String)


 /**
 * Gets all the events from users, collections, datasets, and files and compliles them into 1 list
 */
 def getAllEvents(usersFollowed: List[UUID], collectionsFollowed: List[UUID], datasetsFollowed: List[UUID], filesFollowed: List[UUID]): List[Event]

/**
* Gets all users for a specific list and specific type: object or source
*/

def getAllEventsOfType(following: List[UUID], id_type: String): List[Event]

/**
* Gets all events for one UUID
*/

 def getEvents(id: String, id_type: String): SalatMongoCursor[Event]

}



 