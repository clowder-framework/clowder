package services.mongodb
import com.mongodb.casbah.commons.MongoDBObject
import com.mongodb.util.JSON
import org.elasticsearch.action.search.SearchResponse
import play.api.Logger
import play.api.Play._
import models._
import com.novus.salat.dao.{ModelCompanion, SalatDAO}
import MongoContext.context
import play.api.Play.current
import com.mongodb.casbah.Imports._
import play.api.libs.json.JsValue
import javax.inject.{Inject, Singleton}
import com.mongodb.casbah.commons.TypeImports.ObjectId
import com.mongodb.casbah.WriteConcern
import services.{ContextLDService, CurationService, DatasetService, ElasticsearchPlugin, ExtractorMessage, ExtractorRoutingService, FileService, FolderService, MetadataService}
import api.{Permission, UserRequest}
import controllers.Utils

/**
 * MongoDB Metadata Service Implementation
 */
@Singleton
class MongoDBMetadataService @Inject() (contextService: ContextLDService, datasets: DatasetService, files: FileService,
  folders: FolderService, curations: CurationService, routing: ExtractorRoutingService) extends MetadataService {

  /**
   * Add metadata to the metadata collection and attach to a section /file/dataset/collection
   */
  def addMetadata(metadata: Metadata): UUID = {
    // TODO: Update context
    val mid = MetadataDAO.insert(metadata, WriteConcern.Safe)
    current.plugin[MongoSalatPlugin] match {
      case None => throw new RuntimeException("No MongoSalatPlugin")
      case Some(x) => x.collection(metadata.attachedTo) match {
        case Some(c) => {
          c.update(MongoDBObject("_id" -> new ObjectId(metadata.attachedTo.id.stringify)), $inc("metadataCount" -> +1))
        }
        case None => {
          Logger.error(s"Could not increase counter for ${metadata.attachedTo}")
        }
      }
    }
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
    val order = MongoDBObject("createdAt"-> -1)
    MetadataDAO.find(MongoDBObject("attachedTo.resourceType" -> resourceRef.resourceType.name,
      "attachedTo._id" -> new ObjectId(resourceRef.id.stringify))).sort(order).toList
  }

  /** Get Extractor metadata by attachTo, from a specific extractor if given */
  def getExtractedMetadataByAttachTo(resourceRef: ResourceRef, extractor: String): List[Metadata] = {
    val regex = ".*extractors/"+extractor

    val order = MongoDBObject("createdAt" -> -1)
    MetadataDAO.find(MongoDBObject(
      "attachedTo.resourceType" -> resourceRef.resourceType.name,
      "attachedTo._id" -> new ObjectId(resourceRef.id.stringify),
      // Get only extractors metadata even if specific extractor not given
      "creator.extractorId" -> (regex).r
    )).sort(order).toList
  }

  /** Get metadata based on type i.e. user generated metadata or technical metadata  */
  def getMetadataByCreator(resourceRef: ResourceRef, typeofAgent: String): List[Metadata] = {
    val order = MongoDBObject("createdAt"-> -1)
    val metadata = MetadataDAO.find(MongoDBObject("attachedTo.resourceType" -> resourceRef.resourceType.name,
      "attachedTo._id" -> new ObjectId(resourceRef.id.stringify))).sort(order)

    for (md <- metadata.toList; if (md.creator.typeOfAgent == typeofAgent)) yield md
  }

  /**
   * Update metadata
   * TODO: implement
    *
    * @param metadataId
   * @param json
   */
  def updateMetadata(metadataId: UUID, json: JsValue) = {}

  /** Remove metadata, if this metadata does not exist, nothing is executed. Return removed metadata */
  def removeMetadata(id: UUID) = {
    getMetadataById(id) match {
      case Some(md) => {
        md.contextId.foreach { cid =>
          if (getMetadataBycontextId(cid).length == 1) {
            contextService.removeContext(cid)
          }
        }
        MetadataDAO.remove(md, WriteConcern.Safe)

        // send extractor message after removed from resource
        val mdMap = Map("metadata" -> md.content,
          "resourceType" -> md.attachedTo.resourceType.name,
          "resourceId" -> md.attachedTo.id.toString)

        //update metadata count for resource
        current.plugin[MongoSalatPlugin] match {
          case None => throw new RuntimeException("No MongoSalatPlugin")
          case Some(x) => x.collection(md.attachedTo) match {
            case Some(c) => {
              c.update(MongoDBObject("_id" -> new ObjectId(md.attachedTo.id.stringify)), $inc("metadataCount" -> -1))
            }
            case None => {
              Logger.error(s"Could not decrease counter for ${md.attachedTo}")
            }
          }
        }
      }
      case None => Logger.debug("No metadata found to remove with UUID "+id.toString)
    }
  }

  def getMetadataBycontextId(contextId: UUID) : List[Metadata] = {
    MetadataDAO.find(MongoDBObject("contextId" -> new ObjectId(contextId.toString()))).toList
  }

  def removeMetadataByAttachTo(resourceRef: ResourceRef, host: String, apiKey: Option[String], user: Option[User]): List[UUID] = {
    val metadataDocs = getMetadataByAttachTo(resourceRef)
    metadataDocs.foreach(m => MetadataDAO.remove(m))
    val numRemoved = metadataDocs.size

    //update metadata count for resource
    resourceRef.resourceType.name match {
      case "dataset" => datasets.incrementMetadataCount(resourceRef.id, (-1 * numRemoved))
      case "file" => files.incrementMetadataCount(resourceRef.id, (-1 * numRemoved))
      case "curationObject" => curations.incrementMetadataCount(resourceRef.id, (-1 * numRemoved))
      case _ => Logger.error(s"Could not decrease metadata counter for ${resourceRef}")
    }

    // send extractor message after attached to resource
    metadataDocs.foreach { m =>
      routing.metadataRemovedFromResource(m.id, resourceRef, host, apiKey, user)
    }

    metadataDocs.map(m => m.id)
  }

  /** Remove metadata by attached ID and extractor name **/
  def removeMetadataByAttachToAndExtractor(resourceRef: ResourceRef, extractorName: String, host: String,
    apiKey: Option[String], user: Option[User]): List[UUID] = {
    val regex = ".*extractors/"+(extractorName.trim)
    val metadataDocs = MetadataDAO.find(MongoDBObject("attachedTo.resourceType" -> resourceRef.resourceType.name,
      "attachedTo._id" -> new ObjectId(resourceRef.id.stringify), "creator.extractorId" -> (regex.r))).toList
    metadataDocs.foreach(m => MetadataDAO.remove(m))
    val numRemoved = metadataDocs.size

    //update metadata count for resource
    resourceRef.resourceType.name match {
      case "dataset" => datasets.incrementMetadataCount(resourceRef.id, (-1 * numRemoved))
      case "file" => files.incrementMetadataCount(resourceRef.id, (-1 * numRemoved))
      case "curationObject" => curations.incrementMetadataCount(resourceRef.id, (-1 * numRemoved))
      case _ => Logger.error(s"Could not decrease metadata counter for ${resourceRef}")
    }

    // send extractor message after attached to resource
    metadataDocs.foreach { m =>
      routing.metadataRemovedFromResource(m.id, resourceRef, host, apiKey, user)
    }

    metadataDocs.map(m => m.id)
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
  def getDefinitions(spaceId: Option[UUID] = None): List[MetadataDefinition] = {
    spaceId match {
      case None => MetadataDefinitionDAO.find(MongoDBObject("spaceId" -> null)).toList.sortWith( _.json.\("label").asOpt[String].getOrElse("") < _.json.\("label").asOpt[String].getOrElse("") )
      case Some(s) => MetadataDefinitionDAO.find(MongoDBObject("spaceId" -> new ObjectId(s.stringify))).toList.sortWith( _.json.\("label").asOpt[String].getOrElse("") < _.json.\("label").asOpt[String].getOrElse("") )
    }

  }

  def getDefinitionsDistinctName(user: Option[User]): List[MetadataDefinition] = {
    val filterAccess = if(configuration(play.api.Play.current).getString("permissions").getOrElse("public") == "public") {
      MongoDBObject()
    } else {
      val orlist = scala.collection.mutable.ListBuffer.empty[MongoDBObject]
      orlist += MongoDBObject("spaceId" -> null)
      //TODO: Add public space check.
      user match {
        case Some(u) => {
          val okspaces = u.spaceandrole.filter(_.role.permissions.intersect(Set(Permission.ViewMetadata.toString())).nonEmpty)
          if(okspaces.nonEmpty) {
            orlist += ("spaceId" $in okspaces.map(x=> new ObjectId(x.spaceId.stringify)))
          }
          $or(orlist.map(_.asDBObject))
        }
        case None => MongoDBObject()
      }
    }
    MetadataDefinitionDAO.find(filterAccess).toList.groupBy(_.json).map(_._2.head).toList.sortWith( _.json.\("label").asOpt[String].getOrElse("") < _.json.\("label").asOpt[String].getOrElse("") )
  }

  def getDefinition(id: UUID): Option[MetadataDefinition] = {
    MetadataDefinitionDAO.findOne(MongoDBObject("_id" -> new ObjectId(id.stringify)))
  }

  def getDefinitionByUri(uri:String):Option[MetadataDefinition] = {
    MetadataDefinitionDAO.findOne(MongoDBObject("json.uri" -> uri))
  }

  def getDefinitionByUriAndSpace(uri: String, spaceId: Option[String]): Option[MetadataDefinition] = {
    spaceId match {
      case Some(s) => MetadataDefinitionDAO.findOne(MongoDBObject("json.uri" -> uri, "spaceId" -> new ObjectId(s)))
      case None => MetadataDefinitionDAO.findOne(MongoDBObject("json.uri" -> uri, "spaceId" -> null) )
    }
  }

  def removeDefinitionsBySpace(spaceId: UUID) = {
    MetadataDefinitionDAO.remove(MongoDBObject("spaceId" -> new ObjectId(spaceId.stringify)))
  }

  /** Add vocabulary definitions, leaving it unchanged if the update argument is set to false **/
  def addDefinition(definition: MetadataDefinition, update: Boolean = true): Unit = {
    val uri = (definition.json \ "uri").as[String]
    MetadataDefinitionDAO.findOne(MongoDBObject("json.uri" -> uri)) match {
      case Some(md) => {
        if (update) {
          if(md.spaceId == definition.spaceId) {
            Logger.debug("Updating existing vocabulary definition: " + definition)
            // make sure to use the same id as the old value
            val writeResult = MetadataDefinitionDAO.update(MongoDBObject("json.uri" -> uri), definition.copy(id=md.id),
              false, false, WriteConcern.Normal)
          } else {
            Logger.debug("Adding existing vocabulary definition to a different space" + definition)
            MetadataDefinitionDAO.save(definition)
          }

        } else {
          Logger.debug("Leaving existing vocabulary definition unchanged: " + definition)
        }
      }
      case None => {
        Logger.debug("Adding new vocabulary definition " + definition)
        MetadataDefinitionDAO.save(definition)
      }
    }
  }

  def editDefinition(id: UUID, json: JsValue) = {
    MetadataDefinitionDAO.update(MongoDBObject("_id" ->new ObjectId(id.stringify)),
      $set("json" -> JSON.parse(json.toString()).asInstanceOf[DBObject]) , false, false, WriteConcern.Safe)
  }

  def deleteDefinition(id :UUID): Unit = {
    MetadataDefinitionDAO.remove(MongoDBObject("_id" ->new ObjectId(id.stringify)))
  }

  def searchbyKeyInDataset(key: String, datasetId: UUID): List[Metadata] = {
    val field = "content." + key.trim
    MetadataDAO.find((field $exists true) ++ MongoDBObject("attachedTo.resourceType" -> "dataset") ++ MongoDBObject("attachedTo._id" -> new ObjectId(datasetId.stringify))).toList
  }

  def updateAuthorFullName(userId: UUID, fullName: String) {
    MetadataDAO.update(MongoDBObject("creator._id" -> new ObjectId(userId.stringify), "creator.typeOfAgent" -> "cat:user"),
      $set("creator.user.fullName" -> fullName, "creator.fullName" -> fullName), false, true, WriteConcern.Safe)
  }
}

object MetadataDAO extends ModelCompanion[Metadata, ObjectId] {
  val dao = current.plugin[MongoSalatPlugin] match {
    case None => throw new RuntimeException("No MongoSalatPlugin")
    case Some(x) => new SalatDAO[Metadata, ObjectId](collection = x.collection("metadata")) {}
  }
}

object MetadataDefinitionDAO extends ModelCompanion[MetadataDefinition, ObjectId] {
  val dao = current.plugin[MongoSalatPlugin] match {
    case None => throw new RuntimeException("No MongoSalatPlugin")
    case Some(x) => new SalatDAO[MetadataDefinition, ObjectId](collection = x.collection("metadata.definitions")) {}
  }
}
