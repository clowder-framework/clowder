package models

import java.util.Date
import play.api.libs.json.JsArray
import play.api.libs.json.JsObject
import java.net.URL
/**
 * A piece of metadata for a file
 *
 **/
case class FileMetadata (
  id : UUID = UUID.generate,
  fileId: UUID,
  contextId : Option[UUID] = None,     
  createdAt : Date,
  creator : Agent,
  content : JsObject,
  version : Option[String] = None
)

sealed trait Agent

// User through the GUI
case class UserAgent(id: UUID,  userId: Option[URL]) extends Agent

// Automatic extraction
case class ExtractorAgent(id: UUID,  extractorId: Option[URL]) extends Agent




