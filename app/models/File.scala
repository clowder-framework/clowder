package models

import java.util.Date
import securesocial.core.Identity
import scala.collection.mutable.MutableList
import services._
import javax.inject.{Inject, Singleton}
import play.api.libs.json.Json
import play.api.libs.json.JsValue
import play.api.Logger


/**
 * Uploaded files.
 *
 * @author Luigi Marini
 *
 */
case class File(
  id: UUID = UUID.generate,
  path: Option[String] = None,
  filename: String,
  author: Identity,
  uploadDate: Date,
  contentType: String,
  length: Long = 0,
  showPreviews: String = "DatasetLevel",
  sections: List[Section] = List.empty,
  previews: List[Preview] = List.empty,
  tags: List[Tag] = List.empty,
  metadata: List[Map[String, Any]] = List.empty,
  thumbnail_id: Option[String] = None,
  isIntermediate: Option[Boolean] = None,
  userMetadata: Map[String, Any] = Map.empty,
  xmlMetadata: Map[String, Any] = Map.empty,
  userMetadataWasModified: Option[Boolean] = None,
  notesHTML: Option[String] = None )
  
case class Versus(
 	fileId:UUID,
	descriptors:Map[String,Any]=Map.empty
)
