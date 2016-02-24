package services.mongodb

import java.net.URI
import javax.inject.{Inject, Singleton}

import com.novus.salat.dao.{SalatDAO, ModelCompanion}
import models._
import org.bson.types.ObjectId
import play.api.Play._
import MongoContext.context
import services.{MetadataService, CurationService, SpaceService}
import util.Direction._
import java.util.Date
import play.api.Logger
import com.mongodb.casbah.commons.MongoDBObject
import com.mongodb.casbah.WriteConcern
import com.mongodb.casbah.Imports._


@Singleton
class MongoDBCurationService  @Inject() (metadatas: MetadataService, spaces: SpaceService)  extends CurationService {

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

  def remove(id: UUID): Unit = {
    val curation = get(id)
    curation match {
      case Some(c) => {
        metadatas.removeMetadataByAttachTo(ResourceRef(ResourceRef.curationObject, c.id))
        c.files.map(f => deleteCurationFile(f))
        c.folders.map(f => {
          removeCurationFolder("dataset", id, f)
          deleteCurationFolder(f)
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


  def getCurationObjectByDatasetId(datasetId: UUID): List[CurationObject] = {
    CurationDAO.find(MongoDBObject("datasets" -> MongoDBObject("$elemMatch" -> MongoDBObject("_id" -> new ObjectId(datasetId.stringify))))).toList
  }

  def getCurationFiles(curationFileIds:List[UUID]): List[CurationFile] ={
    (for (cf <- curationFileIds) yield CurationFileDAO.findOneById(new ObjectId(cf.stringify))).flatten
  }

  def getCurationFolders(curationFolderIds:List[UUID]): List[CurationFolder] = {
    (for (cf <- curationFolderIds) yield CurationFolderDAO.findOneById(new ObjectId(cf.stringify))).flatten
  }

  def getCurationFolder(curationFolderId: UUID): Option[CurationFolder] = {
    CurationFolderDAO.findOneById(new ObjectId(curationFolderId.stringify))
  }

  def getCurationByCurationFile(curationFileId: UUID): Option[CurationObject] = {
    CurationDAO.findOne(MongoDBObject("files" ->  new ObjectId(curationFileId.stringify)))
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

  def deleteCurationFile(curationFileId: UUID) : Unit = {
    metadatas.removeMetadataByAttachTo(ResourceRef(ResourceRef.curationFile, curationFileId))
    CurationFileDAO.remove(MongoDBObject("_id" ->new ObjectId(curationFileId.stringify)))
  }

  def deleteCurationFolder(id: UUID): Unit = {
    getCurationFolder(id) match {
      case Some(curationFolder )=> {
        curationFolder.folders.map { cf => {
          removeCurationFolder("folders", id, cf)
          deleteCurationFolder(cf)
        }
        }
        curationFolder.files.map { cf => {
          removeCurationFile("folders", id, cf)
          deleteCurationFile(cf)
        }
        }

      }
      case None =>
    }
    CurationFolderDAO.remove(MongoDBObject("_id" ->new ObjectId(id.stringify)))
  }

  def updateInformation(id: UUID, description: String, name: String, oldSpace: UUID, newSpace:UUID) = {
    val result = CurationDAO.update(MongoDBObject("_id" -> new ObjectId(id.stringify)),
      $set("description" -> description, "name" -> name, "space" -> new ObjectId(newSpace.stringify)),
      false, false, WriteConcern.Safe)
    if(oldSpace != newSpace) {
      spaces.removeCurationObject(oldSpace, id)
      spaces.addCurationObject(newSpace, id)
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