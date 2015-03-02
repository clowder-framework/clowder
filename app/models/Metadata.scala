package models

import java.util.Date
import play.api.libs.json.JsArray
import play.api.libs.json.JsObject
import java.net.URL
import play.api.libs.json.Writes
import play.api.libs.json.Json
import play.api.libs.json.JsValue
/**
 * A piece of metadata for a section/file/dataset/collection/space
 * 
 * @author Luigi Marini
 * @author Smruti Padhy
 *
 **/
case class Metadata (
  id : UUID = UUID.generate,
  attachedTo: Map[String, UUID], //metadata attached to an ElementType e.g. file, dataset, collection, space
                                 //e.g. (file_id->UUID)
  contextId : Option[UUID] = None,     
  createdAt : Date,
  creator : Agent,
  content  : JsValue,
  version : Option[String] = None
)

trait Agent {
  val id: UUID
  def typeOfAgent: String
  def typeOfAgent_= (s: String): Unit
}

// User through the GUI
case class UserAgent( id: UUID, var typeOfAgent : String = "user", userId: Option[URL]) extends Agent

// Automatic extraction
case class ExtractorAgent( id: UUID, var typeOfAgent : String = "extractor" , extractorId: Option[URL]) extends Agent





