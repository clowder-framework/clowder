package models

import java.net.URL
import java.util.Date
import play.api.libs.json.JsObject
import util.SpaceConfig

/**
 * A space defines a partition of the data so that authorization can be enforced on it.
 * It is also a way for users to create collaboration spaces with other users.
 *
 */

case class ProjectSpace (
  id: UUID = UUID.generate,
  name: String = "N/A",
  description: String = "N/A",
  created: Date,
  creator: UUID, // attribution:UUID ?
  homePage: List[URL],
  logoURL: Option[URL],
  bannerURL: Option[URL],
  collectionCount: Integer,
  datasetCount: Integer,
  fileCount: Integer,
  userCount: Integer,
  spaceBytes: Long, 
  metadata: List[Metadata],
  resourceTimeToLive: Long = SpaceConfig.getTimeToLive(),
  isTimeToLiveEnabled: Boolean = SpaceConfig.getIsTimeToLiveEnabled(),
  followers: List[UUID] = List.empty,
  curationObjects: List[UUID] = List.empty,
  invitations: List[(UUID, String)] = List.empty,
  requests: List[RequestResource] = List.empty,
  status: String = SpaceStatus.TRIAL.toString,  // space has three status: trial, private and public. yet users can only see the
  // access of the space, where trial equals to private
  affiliatedSpaces: List[String] = List.empty
){
  def isPublic:Boolean = status == SpaceStatus.PUBLIC.toString
  def isTrial:Boolean = status == SpaceStatus.TRIAL.toString
}

object SpaceStatus extends Enumeration {
  type SpaceStatus = Value
  val TRIAL, PRIVATE, PUBLIC = Value
}

case class UserSpace (
   id: UUID = UUID.generate,
   name: String = "N/A",
   description: String = "N/A",
   homePage: List[URL],
   logoURL: Option[URL],
   bannerURL: Option[URL],
   collectionCount: Integer,
   datasetCount: Integer,
   userCount: Integer,
   spaceBytes: Long)

case class SpaceInvite(
    id: UUID = UUID.generate,
    invite_id: String,
    email: String,
    space: UUID,
    role: String,
    creationTime: java.util.Date,
    expirationTime: java.util.Date)
   
// New way to manage metadata. Will eventually be merged with space metadata.
case class SpaceMetadata (
  created: Date,
  creator: Agent,     // user/extractor/tool
  content: JsObject,
  previousVersion: Option[UUID]
)

// Attempt at a generic provenance object. **Not ready**
case class ProvObj (
  typeofIf: String,
  id: UUID, // id of original object
  archived: Date,
  obj: JsObject)
