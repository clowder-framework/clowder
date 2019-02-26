package services.mongodb

import java.text.SimpleDateFormat
import java.util.Date

import com.mongodb.casbah.Imports.ObjectId
import com.novus.salat.dao.{ModelCompanion, SalatDAO}
import javax.inject.{Inject, Singleton}
import models.{FileLink, MiniUser, UUID}
import play.api.Play.current
import services.{FileLinkService, FileService}
import MongoContext.context
import com.mongodb.casbah.WriteConcern
import com.mongodb.casbah.commons.MongoDBObject
import models.UUIDConversions._

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
  val dao = current.plugin[MongoSalatPlugin] match {
    case None => throw new RuntimeException("No MongoSalatPlugin");
    case Some(x) => new SalatDAO[FileLink, ObjectId](collection = x.collection("downloads.links")) {}
  }
}
