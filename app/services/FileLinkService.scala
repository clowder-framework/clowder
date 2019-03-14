package services

import models.{FileLink, MiniUser, UUID}

trait FileLinkService {

  def createLink(fileId: UUID, author: MiniUser, expireDays: Int): FileLink

  def getLinkByFileId(fileId: UUID): List[FileLink]

  def deleteLink(linkId: UUID)

  def getLinkByLinkId(linkId: UUID): Option[FileLink]

}
