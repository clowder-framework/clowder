package models

import java.net.URL
import java.util.Date
import play.api.libs.json.JsObject

/**
 * A space definines a partition of the data so that authorization can be enforced on it.
 * It is also a way for users to create collaboration spaces with other users.
 *
 * @author Luigi Marini
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
  usersByRole: Map[UUID, List[UUID]], // roleId -> userId
  collectionCount: Integer,
  datasetCount: Integer,
  userCount: Integer,
  metadata: List[Metadata])


case class UserSpace (
   id: UUID = UUID.generate,
   name: String = "N/A",
   description: String = "N/A",
   homePage: List[URL],
   logoURL: Option[URL],
   bannerURL: Option[URL],
   collectionCount: Integer,
   datasetCount: Integer,
   userCount: Integer)

case class Role()

// Attempt at a generic provenance object. **Not ready**
case class ProvObj (
  typeofIf: String,
  id: UUID, // id of original object
  archived: Date,
  obj: JsObject)

