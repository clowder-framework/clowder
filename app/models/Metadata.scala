package models

import java.util.Date
import play.api.libs.json.JsArray
import play.api.libs.json.JsObject
import java.net.URL
import play.api.libs.json.Writes
import play.api.libs.json.Json
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
  def typeOfAgent: String
  def typeOfAgent_= (s: String): Unit
}

// User through the GUI
case class UserAgent( id: UUID, var typeOfAgent : String = "user", userId: Option[URL]) extends Agent
//case class UserAgent( id: UUID, var typeOfAgent : String = "user", userId: Option[String]) extends Agent

// Automatic extraction
case class ExtractorAgent( id: UUID, var typeOfAgent : String = "extractor" , extractorId: Option[URL]) extends Agent
//case class ExtractorAgent( id: UUID, var typeOfAgent : String = "extractor" , extractorId: Option[String]) extends Agent

object UserAgent  {
  implicit val UserAgentWrites = new Writes[UserAgent] {
    def writes(agent: UserAgent) = Json.obj(
      "id" -> agent.typeOfAgent,
      "typeOfAgent" -> agent.typeOfAgent,
      "userId" -> agent.userId.toString
   )
  }
  
}

object Agent{
implicit val AgentWrites = new Writes[Agent] {
    def writes(agent: Agent) = Json.obj(
      "id" -> agent.typeOfAgent,
      "typeOfAgent" -> agent.typeOfAgent    
   )
  }
}

object Metadata{
  implicit val MetadataWrites = new Writes[Metadata] {
    def writes(metadata: Metadata) = Json.obj(
      "id" -> metadata.id,
      "attachedTo"-> metadata.attachedTo, 
      "contextId" -> metadata.contextId,
      "createdAt" -> metadata.createdAt,
      "creator" -> metadata.creator,
      "content"  -> metadata.content,
      "version" -> metadata.version
   )
  }
}


