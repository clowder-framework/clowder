package models

import java.net.URL
import java.util.Date
import play.api.Logger
import play.api.libs.json._
import play.api.data.validation.ValidationError
import services.{UserService, DI, FileService}
import org.apache.jena.riot.RDFDataMgr
import org.apache.jena.riot.RDFLanguages
import org.apache.jena.riot.ReaderRIOT
import org.apache.jena.rdf.model.{Model, ModelFactory}
import org.apache.jena.riot.system._


/**
 * A piece of metadata for a section/file/dataset/collection/space
 **/
case class Metadata (
    id: UUID = UUID.generate,
    attachedTo: ResourceRef,
    contextId: Option[UUID] = None,
    contextURL: Option[URL] = None,
    createdAt: Date = new Date(),
    creator: Agent,
    content: JsValue,
    version: Option[String] = None)

case class MetadataPair(
     label: String,
     content: JsValue)

trait Agent {
  val id: UUID
  def operation: String
  def displayName: String
  def url: Option[URL]
  def typeOfAgent: String
  def typeOfAgent_= (s: String): Unit
}

case class RDFModel(model: org.apache.jena.rdf.model.Model)

// User through the GUI
case class UserAgent(id: UUID, var typeOfAgent: String = "user", user: MiniUser, userId: Option[URL]) extends Agent {
  def operation: String = "Added"
  def displayName: String = user.fullName
  def url: Option[URL] = userId
}

// Automatic extraction
case class ExtractorAgent(id: UUID, var typeOfAgent: String = "extractor", name: Option[String] = None,  extractorId: Option[URL]) extends Agent {
  def operation: String = "Extracted"
  def displayName: String = {
    name match {
      case Some(s) => s
      case None => extractorId.map(_.toString).getOrElse("Unknown")
    }
  }
  def url: Option[URL] = extractorId
}

object Agent {

  implicit object AgentReads extends Reads[Agent] {

    val userService: UserService = DI.injector.getInstance(classOf[UserService])

    def reads(json: JsValue) = {
      //creator(agent) may be User or Extractor depending on the json 
      var creator: Option[models.Agent] = None
      
      //parse json input for type of agent
      val typeOfAgent = (json \ "agent" \ "@type").as[String]

      // parse label if given
      val name = (json \ "agent" \ "name").asOpt[String]

      //if user_id is part of the request, then creator is a user
      val user_id = (json \ "agent" \ "user_id").asOpt[String]
      user_id map { uid =>
        val userId = Some(new URL(uid))
        val profile = """.*/api/users/([^\?]+).*""".r
        val user = uid match {
          case profile(id) => {
            try {
              userService.get(UUID(id)) match {
                case Some(u) => MiniUser(u.id, u.fullName, u.avatarUrl.get, u.email)
                case None => MiniUser(UUID("000000000000000000000000"), name.getOrElse("Unknown"), "", None)
              }
            } catch {
              case e: Exception => MiniUser(UUID("000000000000000000000000"), name.getOrElse("Unknown"), "", None)
            }
          }
          case _ => MiniUser(UUID("000000000000000000000000"), name.getOrElse("Unknown"), "", None)
        }
        creator = Some(UserAgent(UUID.generate, typeOfAgent, user, userId))
      }

      //if extractor_id is part of the request, then creator is an extractor
      val extr_id = (json \ "agent" \ "extractor_id").asOpt[String]
      extr_id map { exid =>
        val extractorId =  Some(new URL(exid))
        creator = Some(ExtractorAgent(UUID.generate, typeOfAgent, name, extractorId))
      }

      //if creator is still None - wrong user input
      creator match {
        case Some(c) => JsSuccess(c)
        case None => JsError(ValidationError("could not get creator"))
      }
    }
  }
}

object Metadata {

  implicit object ExtractorAgentWrites extends Writes[ExtractorAgent] {
    def writes(extractor: ExtractorAgent): JsObject = {
      val extractor_id_string = extractor.extractorId.map(_.toString).getOrElse("")
      Json.obj(
        "@type" -> "cat:extractor",
        "name" -> extractor.displayName,
        "extractor_id" -> extractor_id_string)
    }
  }
 
  implicit object UserAgentWrites extends Writes[UserAgent] {
    def writes(user: UserAgent): JsObject = {
      val user_id_string = user.userId.map(_.toString).getOrElse("")
      Json.obj(
        "@type" -> "cat:user",
        "name" -> user.displayName,
        "user_id" -> user_id_string)
    }
  }
	
  implicit object MetadataWrites extends Writes[Metadata] {
		def writes(metadata: Metadata) = Json.obj(		    
				"created_at" -> metadata.createdAt.toString,
				//if (i == 1) x else y
				//switch based on type of creator/agent and call appropriate class' implicit writes
				"agent"-> (if (metadata.creator.isInstanceOf[UserAgent]) metadata.creator.asInstanceOf[UserAgent] else metadata.creator.asInstanceOf[ExtractorAgent]) ,
				"content" -> metadata.content
				)
	}
}

object RDFModel {

  implicit object RDFModelReads extends Reads[models.RDFModel] {

    def reads(json: JsValue) = {
      var model: Option[models.RDFModel] = None
      var in: java.io.InputStream = new java.io.ByteArrayInputStream( Json.stringify(json).getBytes )
      
      // Parse JSON-LD
      var m: Model = ModelFactory.createDefaultModel()
      var error: String = null
      try {
        m.read(in, "http://example/base", "JSON-LD")
        if(!m.isEmpty) model = Some(RDFModel(m))
      } catch {
        case e: Exception => error = e.getLocalizedMessage
      }
      if(error != null) JsError(ValidationError(error))
      else
        model match {
          case Some(c) => JsSuccess(c)
          case None => JsError(ValidationError("Parse succeeded, but JSON-LD RDF model was empty. Try setting a default @vocab in your @context node."))
        }
    }
    
  }
}