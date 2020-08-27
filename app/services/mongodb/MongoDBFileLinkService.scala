package services.mongodb

import com.mongodb.casbah.Imports.ObjectId
import com.mongodb.casbah.WriteConcern
import com.mongodb.casbah.commons.MongoDBObject
import javax.inject.{Inject, Singleton}
import models.UUIDConversions._
import models.{FileLink, MiniUser, UUID}
import salat.dao.{ModelCompanion, SalatDAO}
import services.mongodb.MongoContext.context
import services.{DI, FileLinkService, FileService}

@Singleton
class MongoDBFileLinkService @Inject() (
  files: FileService) extends FileLinkService {

  override def createLink(fileId: UUID, author: MiniUser, expireDays: Int): FileLink = {
    val fileLink = FileLink.create(fileId, author, expireDays)
    FileLinkDAO.save(fileLink, WriteConcern.Safe)
    fileLink
  }

  override def getLinkByFileId(fileId: UUID): List[FileLink] = {
    FileLinkDAO.find(MongoDBObject("fileId" -> new ObjectId(fileId.stringify))).toList
  }

  override def deleteLink(linkId: UUID): Unit = {
    FileDAO.removeById(linkId)
  }

  override def getLinkByLinkId(linkId: UUID): Option[FileLink] = {
    FileLinkDAO.findOneById(linkId) match {
      case Some(filelink) => {
        val today = new java.util.Date()
        val expireDate = filelink.expire
        if (expireDate.compareTo(today) > 0) {
          Some(filelink)
        } else {
          None
        }
      }
      case None => None
    }
  }
}

object FileLinkDAO extends ModelCompanion[FileLink, ObjectId] {
  val COLLECTION = "downloads.links"
  val mongos: MongoStartup = DI.injector.getInstance(classOf[MongoStartup])
  val dao = new SalatDAO[FileLink, ObjectId](collection = mongos.collection(COLLECTION)) {}
}
