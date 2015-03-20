package services
import models.Event
/**
 * Service definition to interact with Events database.
 *
 * @author Varun Kethineedi
 */
trait EventService {


 def listEvents(): List[Event]

 def addEvent(event: Event)

}
