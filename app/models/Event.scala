package models
import java.util.Date

/**
 * Contains information about an user event
 *
 * @author Varun Kethineedi
 */


 case class Event(
 	user: MiniUser,
 	object_id: Option[UUID] = None,
 	user_object_id: Option[MiniUser] = None,
 	source_id: Option[UUID] = None,
 	event_type: String,
 	created: Date
 )