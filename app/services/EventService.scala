package services
import models.Event
import models.User
import models.UUID
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






}



 