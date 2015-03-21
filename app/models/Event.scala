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
 	object_name: Option[String] = None,
 	source_id: Option[UUID] = None,
 	source_name: Option[String] = None,
 	event_type: String,
 	created: Date
 )