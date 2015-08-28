package services.mongodb
import com.mongodb.casbah.commons.MongoDBObject
import com.mongodb.util.JSON
import play.api.Logger
import models._
import com.novus.salat.dao.{ModelCompanion, SalatDAO}
import MongoContext.context
import play.api.Play.current
import com.mongodb.casbah.Imports._
import play.api.libs.json.{Json, JsValue}
import javax.inject.{Inject, Singleton}
import com.mongodb.casbah.commons.TypeImports.ObjectId
import com.mongodb.casbah.WriteConcern
import services.MetadataService
import services.ContextLDService

/**
 * MongoDB Metadata Service Implementation
 */
@Singleton
class MongoDBMetadataService @Inject() (contextService: ContextLDService) extends MetadataService {

  /**
   * Add metadata to the metadata collection and attach to a section /file/dataset/collection
   */
  def addMetadata(metadata: Metadata): UUID = {
    Logger.debug("Inside addMetadata")
    // TODO: Update context
    val mid = MetadataDAO.insert(metadata, WriteConcern.Safe)
    UUID(mid.get.toString())
  }

  def getMetadataById(id: UUID): Option[Metadata] = {
    MetadataDAO.findOneById(new ObjectId(id.stringify)) match {
      case Some(metadata) => {
        //TODO link to context based on context id
        Some(metadata)
      }
      case None => None
    }
  }

  /** Get Metadata based on Id of an element (section/file/dataset/collection) */
  def getMetadataByAttachTo(resourceRef: ResourceRef): List[Metadata] = {
    MetadataDAO.find(MongoDBObject("attachedTo.resourceType" -> resourceRef.resourceType.name,
      "attachedTo._id" -> new ObjectId(resourceRef.id.stringify))).toList
  }

  /** Get metadata based on type i.e. user generated metadata or technical metadata  */
  def getMetadataByCreator(resourceRef: ResourceRef, typeofAgent: String): List[Metadata] = {
    val metadata = MetadataDAO.find(MongoDBObject("attachedTo.resourceType" -> resourceRef.resourceType.name,
      "attachedTo._id" -> new ObjectId(resourceRef.id.stringify)))

    for (md <- metadata.toList; if (md.creator.typeOfAgent == typeofAgent)) yield md
  }

  /** Remove metadata */
  def removeMetadata(id: UUID) = {
    val md = getMetadataById(id).getOrElse(null)
    MetadataDAO.remove(md, WriteConcern.Safe)
  }
  
  /** Get metadata context if available  **/
  def getMetadataContext(metadataId: UUID): Option[JsValue] = {
    val md = getMetadataById(metadataId)
    md match {
      case Some(m) => {
        val contextId = m.contextId
        contextId match {
          case Some(id) => contextService.getContextById(id)
          case None => None
        }
      }
      case None => None
    }
  }

  /** Vocabulary definitions for user fields **/
  def getVocabularies(spaceId: Option[UUID] = None): List[MDVocabularyDefinition] = {
    MDVocabularyDefinitionDAO.findAll().toList
  }

  /** Add vocabulary definitions **/
  def addVocabularyDefinition(definition: MDVocabularyDefinition): Unit = {
    val uri = (definition.json \ "uri").as[String]
    MDVocabularyDefinitionDAO.findOne(MongoDBObject("json.uri" -> uri)) match {
        // FIXME doesn't seem to propery update
      case Some(md) => MDVocabularyDefinitionDAO.update(MongoDBObject("json.uri" -> uri), definition,
        false, false, WriteConcern.Normal)
      case None => MDVocabularyDefinitionDAO.save(definition)
    }
  }
  
  /** update Metadata 
   *  TODO
   *  */  
  def updateMetadata(metadataId: UUID, json: JsValue) = {}

  def search(query: JsValue): List[ResourceRef] = {
    val doc = JSON.parse(Json.stringify(query)).asInstanceOf[DBObject]
    val resources: List[ResourceRef] = MetadataDAO.find(doc).map(_.attachedTo).toList
    resources
  }

}

object MetadataDAO extends ModelCompanion[Metadata, ObjectId] {
  val dao = current.plugin[MongoSalatPlugin] match {
    case None => throw new RuntimeException("No MongoSalatPlugin")
    case Some(x) => new SalatDAO[Metadata, ObjectId](collection = x.collection("metadata")) {}
  }
}

object MDVocabularyDefinitionDAO extends ModelCompanion[MDVocabularyDefinition, ObjectId] {
  val dao = current.plugin[MongoSalatPlugin] match {
    case None => throw new RuntimeException("No MongoSalatPlugin")
    case Some(x) => new SalatDAO[MDVocabularyDefinition, ObjectId](collection = x.collection("metadata.definitions")) {}
  }
}