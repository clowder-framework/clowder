package models

import java.util.Date
import play.api.libs.json.JsArray
import play.api.libs.json.JsObject
import java.net.URL
/**
 * A piece of metadata for a section/file/dataset/collection/space
 * 
 * @author Luigi Marini
 * @author Smruti Padhy
 *
 **/
case class Metadata (
  id : UUID = UUID.generate,
  attachedTo: Map[String, UUID], //metadata attached to a preview/section/file/dataset/collection/space, e.g. (file_id->UUID)
  contextId : Option[UUID] = None,     
  createdAt : Date,
  creator : Agent,
  content : JsObject,
  version : Option[String] = None
)

trait Agent {
  val id: UUID
  val typeOfAgent: String
}

// User through the GUI
case class UserAgent( id: UUID, typeOfAgent : String = "user", userId: Option[URL]) extends Agent

// Automatic extraction
case class ExtractorAgent( id: UUID, typeOfAgent : String = "extractor" , extractorId: Option[URL]) extends Agent




