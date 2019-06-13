package services.mongodb

import java.net.URI
import javax.inject.{Inject, Singleton}

import api.Permission
import api.Permission._
import com.novus.salat.dao.{SalatDAO, ModelCompanion}
import models._
import org.bson.types.ObjectId
import play.api.Play._
import MongoContext.context
import services.{EventService, MetadataService, CurationService, SpaceService}
import util.Direction._
import java.util.Date
import play.api.Logger
import com.mongodb.casbah.commons.MongoDBObject
import com.mongodb.casbah.WriteConcern
import com.mongodb.casbah.Imports._
import util.Formatters


@Singleton
class MongoDBCurationService  @Inject() (metadatas: MetadataService, spaces: SpaceService, events: EventService)  extends CurationService {

  def insert(curation: CurationObject) = {

      //CurationDAO.save(curation)
      Logger.debug("insert a new CO with ID: " + curation.id)
      CurationDAO.insert(curation)
      spaces.addCurationObject(curation.space, curation.id)
  }

  def get(id: UUID): Option[CurationObject]  = {
    CurationDAO.findOneById(new ObjectId(id.stringify))
  }

  def updateStatus(id: UUID, status: String) {
    CurationDAO.update(MongoDBObject("_id" -> new ObjectId(id.stringify)), $set("status" -> status), false, false, WriteConcern.Safe)
  }

  def setSubmitted(id: UUID) {
    CurationDAO.update(MongoDBObject("_id" -> new ObjectId(id.stringify)), $set("status" -> "Submitted", "submittedDate" -> Some(new Date())), false, false, WriteConcern.Safe)
  }

  def setPublished(id: UUID) {
    CurationDAO.update(MongoDBObject("_id" -> new ObjectId(id.stringify)), $set("status" -> "Published", "publishedDate" -> Some(new Date())), false, false, WriteConcern.Safe)
  }

  def remove(id: UUID, host: String, apiKey: Option[String], user: Option[User]): Unit = {
    val curation = get(id)
    curation match {
      case Some(c) => {
        metadatas.removeMetadataByAttachTo(ResourceRef(ResourceRef.curationObject, c.id), host, apiKey, user)
        c.files.map(f => deleteCurationFile(f, host, apiKey, user))
        c.folders.map(f => {
          removeCurationFolder("dataset", id, f)
          deleteCurationFolder(f, host, apiKey, user)
        })
        spaces.removeCurationObject(c.space, c.id)
        CurationDAO.remove(MongoDBObject("_id" ->new ObjectId(id.stringify)))
      }
      case None =>
    }
  }

  def insertFile(cf: CurationFile) ={
    CurationFileDAO.insert(cf)
  }

  def insertFolder(cf: CurationFolder) ={
    CurationFolderDAO.insert(cf)
  }

  def updateRepository(curationId: UUID, repository: String): Unit = {
    CurationDAO.update(MongoDBObject("_id" -> new ObjectId(curationId.stringify)), $set("repository" -> repository),
      false, false, WriteConcern.Safe)
  }

  def updateExternalIdentifier(curationId: UUID, externalIdentifier: URI): Unit = {
    CurationDAO.update(MongoDBObject("_id" -> new ObjectId(curationId.stringify)), $set("externalIdentifier" -> externalIdentifier),
      false, false, WriteConcern.Safe)
  }

  def updateAuthorFullName(userId: UUID, fullName: String) {
    CurationDAO.update(MongoDBObject("author._id" -> new ObjectId(userId.stringify)),
      $set("author.fullName" -> fullName), false, true, WriteConcern.Safe)
    CurationDAO.update(MongoDBObject("datasets.author._id" -> new ObjectId(userId.stringify)),
      $set("datasets.0.author.fullName" -> fullName), false, true, WriteConcern.Safe)
    CurationFileDAO.update(MongoDBObject("author._id" -> new ObjectId(userId.stringify)),
      $set("author.fullName" -> fullName), false, true, WriteConcern.Safe)
    CurationFolderDAO.update(MongoDBObject("author._id" -> new ObjectId(userId.stringify)),
      $set("author.fullName" -> fullName), false, true, WriteConcern.Safe)
  }

  /**
    * Return a list of curation objects in a space, this does not check for permissions
    */
  def listSpace(limit: Option[Integer], space: Option[String]): List[CurationObject] = {
    val (filter, sort) = filteredQuery(None, false, space, None, None)
    CurationDAO.find(filter).sort(sort).limit(limit.get).toList
  }

  def listSpace(date: String, nextPage: Boolean, limit: Option[Integer], space: Option[String]): List[CurationObject] = {
    val (filter, sort) = filteredQuery(Some(date), nextPage, space, None, None)
    if (date.isEmpty || nextPage) {
      CurationDAO.find(filter).sort(sort).limit(limit.get).toList
      //val space = Option("111")
      //CurationDAO.find(MongoDBObject("space" -> new ObjectId(space.get)) ++ ("created" $lt Formatters.iso8601("2016-09-28T01:04:12.749-05"))).limit(limit.get).toList
    } else {
      CurationDAO.find(filter).sort(sort).limit(limit.get).toList.reverse
    }
  }

  /**
    * Create a filters and sorts based on the given parameters
    */
  private def filteredQuery(date: Option[String], nextPage: Boolean, space: Option[String], status: Option[String], owner: Option[User]): (DBObject, DBObject) = {
    val filterStatus = status match {
      case Some(fs) => MongoDBObject ("status" -> new ObjectId(fs))
      case None => MongoDBObject()
    }
    val filterOwner = owner match {
      case Some(o) => MongoDBObject("author._id" -> new ObjectId(o.id.stringify))
      case None => MongoDBObject()
    }
    val filterSpace = space match {
      case Some(s) => MongoDBObject("space" -> new ObjectId(s))
      case None => MongoDBObject()
    }
    val filterDate = date match {
      case Some(d) => {
        if (nextPage) {
          ("created" $lt Formatters.iso8601(d))
        } else {
          ("created" $gt Formatters.iso8601(d))
        }
      }
      case None => MongoDBObject()
    }
    val sort = if (date.isDefined && !nextPage) {
      MongoDBObject("created"-> 1) ++ MongoDBObject("name" -> 1)
    } else {
      MongoDBObject("created" -> -1) ++ MongoDBObject("name" -> 1)
    }

    (filterStatus ++ filterDate ++ filterSpace ++ filterOwner, sort)
  }

  /** Change the metadataCount field for a curation object */
  def incrementMetadataCount(id: UUID, count: Long) = {
    CurationDAO.update(MongoDBObject("_id" -> new ObjectId(id.stringify)), $inc("metadataCount" -> count), false, false, WriteConcern.Safe)
  }

  def getCurationObjectByDatasetId(datasetId: UUID): List[CurationObject] = {
    CurationDAO.find(MongoDBObject("datasets" -> MongoDBObject("$elemMatch" -> MongoDBObject("_id" -> new ObjectId(datasetId.stringify))))).toList
  }

  def getCurationFiles(curationFileIds:List[UUID]): List[CurationFile] ={
    (for (cf <- curationFileIds) yield CurationFileDAO.findOneById(new ObjectId(cf.stringify))).flatten
  }

  def getCurationFolders(curationFolderIds:List[UUID]): List[CurationFolder] = {
    (for (cf <- curationFolderIds) yield CurationFolderDAO.findOneById(new ObjectId(cf.stringify))).flatten
  }

  def getAllCurationFileIds(id:UUID): List[UUID] ={
    get(id) match {
      case Some(c) => c.files ++ c.folders.map(subf => getAllCurationFileIdsbyCurationFolder(subf)).flatten
      case None => List.empty
    }
  }

  private def getAllCurationFileIdsbyCurationFolder(id:UUID): List[UUID] ={
    getCurationFolder(id)  match {
      case Some(f) => f.files ++ f.folders.map(subf => getAllCurationFileIdsbyCurationFolder(subf)).flatten
      case None => List.empty
    }
  }

  def getAllCurationFolderIds(id: UUID): List[UUID] = {
    get(id) match {
      case Some(c) => c.folders ++ c.folders.map(subf => getAllCurationFolderIdsByCurationFolder(subf)).flatten
      case None => List.empty
    }
  }

  private def getAllCurationFolderIdsByCurationFolder(id: UUID): List[UUID] ={
    getCurationFolder(id) match {
      case Some(f) => f.folders ++ f.folders.map(subf => getAllCurationFolderIdsByCurationFolder(subf)).flatten
      case None => List.empty
    }
  }

  def getCurationFolder(curationFolderId: UUID): Option[CurationFolder] = {
    CurationFolderDAO.findOneById(new ObjectId(curationFolderId.stringify))
  }

  def getCurationByCurationFile(curationFileId: UUID): Option[CurationObject] = {
    CurationFolderDAO.findOne(MongoDBObject("files" ->  new ObjectId(curationFileId.stringify))) match {
      case Some(cf) =>  CurationDAO.findOne(MongoDBObject("_id" ->  new ObjectId(cf.parentCurationObjectId.stringify)))
      case None => CurationDAO.findOne(MongoDBObject("files" ->  new ObjectId(curationFileId.stringify)))
    }
  }

  def addCurationFile(parentType: String, parentId: UUID, curationFileId: UUID) = {
    if(parentType == "dataset") {
      CurationDAO.update(MongoDBObject("_id" -> new ObjectId(parentId.stringify)), $addToSet("files" -> new ObjectId(curationFileId.stringify)), false, false, WriteConcern.Safe)
    } else {
      CurationFolderDAO.update(MongoDBObject("_id" -> new ObjectId(parentId.stringify)), $addToSet("files" -> new ObjectId(curationFileId.stringify)), false, false, WriteConcern.Safe)
    }
  }

  def removeCurationFile(parentType: String, parentId: UUID, curationFileId: UUID) = {
    if(parentType == "dataset") {
      CurationDAO.update(MongoDBObject("_id" -> new ObjectId(parentId.stringify)), $pull("files" -> new ObjectId(curationFileId.stringify)), false, false, WriteConcern.Safe)
    } else {
      CurationFolderDAO.update(MongoDBObject("_id" -> new ObjectId(parentId.stringify)), $pull("files" -> new ObjectId(curationFileId.stringify)), false, false, WriteConcern.Safe)
    }
  }

  def addCurationFolder(parentType: String, parentId: UUID, subCurationFolderId: UUID) = {
    if (parentType == "dataset") {
      CurationDAO.update(MongoDBObject("_id" -> new ObjectId(parentId.stringify)), $addToSet("folders" -> new ObjectId(subCurationFolderId.stringify)), false, false, WriteConcern.Safe)
    } else {
      CurationFolderDAO.update(MongoDBObject("_id" -> new ObjectId(parentId.stringify)), $addToSet("folders" -> new ObjectId(subCurationFolderId.stringify)), false, false, WriteConcern.Safe)
      CurationFolderDAO.update(MongoDBObject("_id" -> new ObjectId(subCurationFolderId.stringify)), $set("parentId" -> new ObjectId(parentId.stringify)), false, false, WriteConcern.Safe)
      CurationFolderDAO.update(MongoDBObject("_id" -> new ObjectId(subCurationFolderId.stringify)), $set("parentType" -> "folder"), false, false, WriteConcern.Safe)
    }
  }

  def removeCurationFolder(parentType: String, parentId: UUID, subCurationFolderId: UUID) = {
    if (parentType == "dataset") {
      CurationDAO.update(MongoDBObject("_id" -> new ObjectId(parentId.stringify)), $pull("folders" -> new ObjectId(subCurationFolderId.stringify)), false, false, WriteConcern.Safe)
    } else {
      CurationFolderDAO.update(MongoDBObject("_id" -> new ObjectId(parentId.stringify)), $pull("folders" -> new ObjectId(subCurationFolderId.stringify)), false, false, WriteConcern.Safe)
    }
  }

  def deleteCurationFile(curationFileId: UUID, host: String, apiKey: Option[String], user: Option[User]) : Unit = {
    metadatas.removeMetadataByAttachTo(ResourceRef(ResourceRef.curationFile, curationFileId), host: String, apiKey, user)
    CurationFileDAO.remove(MongoDBObject("_id" ->new ObjectId(curationFileId.stringify)))
  }

  def deleteCurationFolder(id: UUID, host: String, apiKey: Option[String], user: Option[User]): Unit = {
    getCurationFolder(id) match {
      case Some(curationFolder )=> {
        curationFolder.folders.map { cf => {
          removeCurationFolder("folders", id, cf)
          deleteCurationFolder(cf, host, apiKey, user)
        }
        }
        curationFolder.files.map { cf => {
          removeCurationFile("folders", id, cf)
          deleteCurationFile(cf, host, apiKey, user)
        }
        }

      }
      case None =>
    }
    CurationFolderDAO.remove(MongoDBObject("_id" ->new ObjectId(id.stringify)))
  }

  def updateInformation(id: UUID, description: String, name: String, oldSpace: UUID, newSpace:UUID, creators: List[String]) = {
    get(id) match {
      case Some(c) if name != c.name => {
        events.updateObjectName(id, name)
      }
      case _ => 
    }
    CurationDAO.update(MongoDBObject("_id" -> new ObjectId(id.stringify)),
      $set("description" -> description, "name" -> name, "space" -> new ObjectId(newSpace.stringify), "creators" -> creators),
      false, false, WriteConcern.Safe)
    if(oldSpace != newSpace) {
      spaces.removeCurationObject(oldSpace, id)
      spaces.addCurationObject(newSpace, id)
    }
  }

  def maxCollectionDepth(curation: CurationObject ): Int = {
    val folders = getCurationFolders(curation.folders)
    if(folders.length == 0) {
      return 0
    }
    var maxValue = 0
    curation.folders.foreach{ folder =>
      val depth = maxFolderDepth(folder)
      if(depth > maxValue) {
        maxValue = depth
      }
    }
    return maxValue +1
  }

  private def maxFolderDepth(folderId: UUID): Int = {
    getCurationFolder(folderId) match {
      case Some(folder) => {
        if(folder.folders.length == 0) {
          return 0
        }
        else {
          var maxValue = 0
          folder.folders.foreach{ subf =>
            val depth = maxFolderDepth(subf)
            if(depth > maxValue) {
              maxValue = depth
            }
          }
          return maxValue + 1
        }
      }
      case None =>  return 0
    }
  }
}

/**
 * Salat CurationObject model companion.
 */
object CurationDAO extends ModelCompanion[CurationObject, ObjectId] {
  val dao = current.plugin[MongoSalatPlugin] match {
    case None => throw new RuntimeException("No MongoSalatPlugin");
    case Some(x) => new SalatDAO[CurationObject, ObjectId](collection = x.collection("curationObjects")) {}
  }
}


/**
 * Salat CurationObjectMetadata model companion.
 */
object CurationFileDAO extends ModelCompanion[CurationFile, ObjectId] {
  val dao = current.plugin[MongoSalatPlugin] match {
    case None => throw new RuntimeException("No MongoSalatPlugin");
    case Some(x) => new SalatDAO[CurationFile, ObjectId](collection = x.collection("curationFiles")) {}
  }
}


/**
 * Salat CurationObjectMetadata model companion.
 */
object CurationFolderDAO extends ModelCompanion[CurationFolder, ObjectId] {
  val dao = current.plugin[MongoSalatPlugin] match {
    case None => throw new RuntimeException("No MongoSalatPlugin");
    case Some(x) => new SalatDAO[CurationFolder, ObjectId](collection = x.collection("curationFolders")) {}
  }
}