package services
import models._
import play.api.libs.json.JsValue
import com.mongodb.casbah.Imports._
import java.util.Date


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
	 * Event  where 2 user interacts with 1 object, etc request event
	 */
	def addRequestEvent(user: Option[User], targetuser: User, object_id: UUID, object_name: String,  action_type: String)

	 /**
	 * Gets limit number of events from users, collections, datasets, and files and compliles them into 1 list
	 */
	 def getEvents(followedEntities:List[TypedID], limit: Option[Integer]): List[Event]

	/**
	* Gets all users for a specific list and specific type: object or source
	*/

	def getEventsOfType(following: List[UUID], id_type: String, limit: Option[Integer]): List[Event]

	/**
	* Gets all events for one UUID
	*/
	def getEvents(id: String, id_type: String, limit: Option[Integer]): SalatMongoCursor[Event]

	/**
	* Get limit number of events which come after a specificied time
	*/

	def getEventsByTime(followedEntities:List[TypedID], time: Date, limit: Option[Integer]): List[Event]

	/**
	 * Get the latest N events
	 */
	def getLatestNEventsOfType(n: Int, event_type: Option[String]): List[Event]

	/**
	 * Get the request event and loginuser is targetuser
	 */
	def getRequestEvents( targetuser: Option[User], limit: Option[Integer]): List[Event]

}



