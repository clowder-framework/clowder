package models

import java.util.Date

/**
  * Shareable link to access file without an account. Anyone with the link can access the file.
  */
case class FileLink (
  id: UUID = UUID.generate,
  fileId: UUID,
  author: MiniUser,
  expire: Date)


object FileLink {
  def create(fileId: UUID, author: MiniUser, expireDays: Int): FileLink = {
    import java.util.Calendar
    val calendar = Calendar.getInstance
    calendar.add(Calendar.DAY_OF_YEAR, expireDays)
    val expireDate = calendar.getTime
    create(fileId, author, expireDate)
  }

  def create(fileId: UUID, author: MiniUser, expire: Date): FileLink = {
    new FileLink(fileId = fileId, author = author, expire = expire)
  }
}
