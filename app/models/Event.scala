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


 /** event_type:
 * follow_user, unfollow_user, follow_file, unfollow_file, follow_dataset, unfollow_datset, follow_collection, unfollow_collection => "user follows/unfollows object_name"
 *
 * edit_profile => "user edited his profile"
 *
 * upload_file => "user uploaded object_name"
 *
 * create_dataset, create_collection => "user created dataset/collection: object_name"
 *
 * delete_file, delete_dataset, delete_collection => "user deleted dataset/collection/file: object_name"
 * 
 * add_tags_dataset, add_tags_file => "user added tags to dataset/file: object_name"
 *
 * remove_tags_dataset, remove_tags_file => "user removed tags from dataset/file: object_name"
 *
 * attach_file_dataset => "user added file: object_name to dataset: source_name"
 * detach_file_dataset => "user removed file: object_name from dataset: source_name"
 *
 * attach_dataset_collection => "user added dataset: object_name to collection: source_name"
 * remove_dataset_collection => "user removed dataset: object_name from collection: source_name"
 *
 * addMetadata_dataset => "user added metadata to dataset: object_name"
 * addMetadata_file => "user added metadata to file: object_name"
 * 
 * update_dataset_information => "user updated dataset infomration for object_name"
 * 
 * (when working with comments object_name holds comment text, object_id has UUID of comment)
 * comment_file => "user commented object_name on file: source_name"
 * add_comment_dataset => "user commented object_name on dataset: source_name"
 * edit_comment => "user edited his comment to object_name"
 *
 * set_note_file => "user set the note on object_name"
 *
 * download_file => "user downladed object_name" (not working)
 *
 *
 * To get all events use:
 * var events = events.getAllEvents(muser.followedUsers, muser.followedCollections, muser.followedDatasets, muser.followedFiles)
 *   events is a list of Event objects
 * for (event <- events) (whatever you want with the event)
 *
 *
 *
 *
 */