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
import play.api.libs.json.{JsObject, JsString, Json, JsValue, JsArray}
import javax.inject.{Inject, Singleton}
import com.mongodb.casbah.commons.TypeImports.ObjectId
import com.mongodb.casbah.WriteConcern
import services.{ContextLDService, DatasetService, FileService, FolderService, ExtractorMessage, RabbitmqPlugin, MetadataService, ElasticsearchPlugin}
import api.Permission
import scala.collection.mutable
import scala.util.control.Breaks._

/**
 * MongoDB Metadata Service Implementation
 */
@Singleton
class MongoDBMetadataService @Inject() (contextService: ContextLDService, datasets: DatasetService, files: FileService, folders: FolderService) extends MetadataService {

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
    // send extractor message after attached to resource
    val mdMap = Map("metadata"->metadata.content,
      "resourceType"->metadata.attachedTo.resourceType.name,
      "resourceID"->metadata.attachedTo.id.toString)
    current.plugin[RabbitmqPlugin].foreach { p =>
      val dtkey = s"${p.exchange}.metadata.added"
      p.extract(ExtractorMessage(UUID(""), UUID(""), "", dtkey, mdMap, "", metadata.attachedTo.id, ""))
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

  /** Remove metadata, if this metadata does exit, nothing is executed */
  def removeMetadata(id: UUID) = {
    getMetadataById(id) match {
      case Some(md) =>
        md.contextId.foreach{cid =>
          if (getMetadataBycontextId(cid).length == 1) {
            contextService.removeContext(cid)
          }
        }
        MetadataDAO.remove(md, WriteConcern.Safe)

        // send extractor message after removed from resource
        val mdMap = Map("metadata"->md.content,
          "resourceType"->md.attachedTo.resourceType.name,
          "resourceId"->md.attachedTo.id.toString)
        current.plugin[RabbitmqPlugin].foreach { p =>
          val dtkey = s"${p.exchange}.metadata.removed"
          p.extract(ExtractorMessage(UUID(""), UUID(""), "", dtkey, mdMap, "", md.attachedTo.id, ""))
        }

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
  }

  def getMetadataBycontextId(contextId: UUID) : List[Metadata] = {
    MetadataDAO.find(MongoDBObject("contextId" -> new ObjectId(contextId.toString()))).toList
  }

  def removeMetadataByAttachTo(resourceRef: ResourceRef) = {
    MetadataDAO.remove(MongoDBObject("attachedTo.resourceType" -> resourceRef.resourceType.name,
      "attachedTo._id" -> new ObjectId(resourceRef.id.stringify)), WriteConcern.Safe)
    //not providing metaData count modification here since we assume this is to delete the metadata's host

    // send extractor message after attached to resource
    current.plugin[RabbitmqPlugin].foreach { p =>
      val dtkey = s"${p.exchange}.metadata.removed"
      p.extract(ExtractorMessage(UUID(""), UUID(""), "", dtkey, Map[String, Any](
        "resourceType"->resourceRef.resourceType.name,
        "resourceId"->resourceRef.id.toString), "", resourceRef.id, ""))
    }
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

  def extractJsonKeys(json: JsValue): collection.Set[String] = json match {
    // from http://stackoverflow.com/questions/26650354/get-all-keys-of-play-api-libs-json-jsvalue
    case o: JsObject => o.keys ++ o.values.flatMap(extractJsonKeys)
    case JsArray(as) => as.flatMap(extractJsonKeys).toSet
    case _ => Set()
  }

  def getAutocompleteName(user: Option[User], filter: String): List[String] = {
    // Get list of metadata objects where this field name appears
    val mdlist = MetadataDAO.find("content."+filter $exists true).toList

    var allKeys = List[String]()

    // Filter only to those keys
    mdlist.map(md => {
      val mdkeys = extractJsonKeys(md.content).toList
      allKeys = allKeys.union(mdkeys).filter(k => k matches filter).distinct
    })

    return allKeys
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

  /**
    * Search by metadata. Uses mongodb query structure.
    */
  def search(query: JsValue): List[ResourceRef] = {
    val doc = JSON.parse(Json.stringify(query)).asInstanceOf[DBObject]
    val resources: List[ResourceRef] = MetadataDAO.find(doc).map(_.attachedTo).toList
    resources
  }

  def search(key: String, value: String, extractorName: String, count: Int, user: Option[User]): List[ResourceRef] = {
    val field = "content." + key.trim
    val trimOr = value.trim().replaceAll(" ", "|")
    // for some reason "/"+value+"/i" doesn't work because it gets translate to
    // { "content.Abstract" : { "$regex" : "/test/i"}}
    val regexp = (s"""(?i)$trimOr""").r
    val doc = if (extractorName != "")
      MongoDBObject(field -> regexp, "creator.extractorId" -> (extractorName+"$").r)
    else
      MongoDBObject(field -> regexp)

    var filter = doc
    Logger.debug(filter.toString)
    if (!(configuration(play.api.Play.current).getString("permissions").getOrElse("public") == "public")) {
      user match {
        case Some(u) => {
          val datasetsList = datasets.listUser(u)
          val foldersList = folders.findByParentDatasetIds(datasetsList.map(x => x.id))
          val fileIds = datasetsList.map(x => x.files) ++ foldersList.map(x => x.files)
          val orlist = collection.mutable.ListBuffer.empty[MongoDBObject]
          datasetsList.map { x => orlist += MongoDBObject("attachedTo.resourceType" -> "dataset") ++ MongoDBObject("attachedTo._id" -> new ObjectId(x.id.stringify)) }
          fileIds.flatten.map { x => orlist += MongoDBObject("attachedTo.resourceType" -> "file") ++ MongoDBObject("attachedTo._id" -> new ObjectId(x.stringify)) }
          filter = $or(orlist.map(_.asDBObject)) ++ doc
        }
        case None => List.empty
      }
    }
    val resources: List[ResourceRef] = MetadataDAO.find(filter).limit(count).map(_.attachedTo).toList
    resources
  }

  def searchMultiple(query: List[JsValue], joinType: String, count: Int, user: Option[User]): List[ResourceRef] = {
    Logger.debug("search multiple")
    var results = mutable.MutableList[ResourceRef]()

    current.plugin[ElasticsearchPlugin] match {
      case Some(plugin) => {

        // Prepare query string from JSON objects
        var qString = ""
        query.foreach(jsq => {
          val extr = (jsq \ "extractor_key").toString
          val key = (jsq \ "field_leaf_key").toString
          val operator = (jsq \ "operator").toString.replace("\"", "")
          val value = (jsq \ "field_value").toString.replace("\"", "")

          // Prepend AND/OR if this is 2nd+ term of query
          if (qString != "")
            qString += joinType+" "
          // Add extractor to query if provided
          val qKey = if (extr == "null") key
                     else extr+" AND "+key

          if ((operator == "!=") || (operator == "<") || (operator == ">"))
            // Don't include value if "not equals", "greater/less than" operators - we don't want to match specific value
            qString += "("+key+") "
          else if (operator == ":")
            // Add wildcards if "contains" operator
            qString += "("+qKey+" AND *"+value+"*) "
          else
            qString += "("+qKey+" AND "+value+") "
        })

        //plugin.searchComplex("data", Array[String]("metadata"), query)

        val result: SearchResponse = plugin.search(qString, Array[String]("metadata"))
        for (hit <- result.getHits().getHits()) {
          // Check if search result has any metadata
          // TODO: For 'Advanced Search' should this no longer be restricted to Metadata?
          val md = hit.getSource().get("metadata")
          if (md != null) {
            var jtest = Json.parse(md.toString)
            // Check if this document matches any/all criteria as required
            var hitMatchesAllQueryTerms = true
            var hitMatchesAnyQueryTerm = false
            query.foreach(jsq => {
              val key = (jsq \ "field_leaf_key").toString.replace("\"", "")
              val value = (jsq \ "field_value").toString.replace("\"", "")
              var oper = (jsq \ "operator").toString.replace("\"", "")

              // Check if metadata has chosen key, filtering to specified extractor sub-metadata if necessary
              val values: Seq[JsValue] = (jtest \\ key)
              // Check if any keys found contain the value and add to results if so
              values.map(v => {
                val cval = v.toString.replace("\"","")

                Logger.debug("check "+cval+oper+value)

                if (  ((cval == value) && oper == "==") ||
                      ((cval contains value) && oper == ":") ||
                      (!(cval == value) && oper == "!=") ||
                      ((cval < value) && oper == "<") ||
                      ((cval > value) && oper == ">")
                )
                  hitMatchesAnyQueryTerm = true
                else
                  hitMatchesAllQueryTerms = false
              })
            })
            if (  (hitMatchesAnyQueryTerm && (joinType == "OR")) ||
                  (hitMatchesAllQueryTerms && (joinType == "AND"))) {
              // TODO: Check permissions of this resource before adding to list
              results += new ResourceRef(Symbol(hit.getType()), UUID(hit.getId()))
            }
          }
        }
      }
      case None => Logger.error("ElasticSearch plugin could not be reached for metadata search")
    }
    results.toList
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
