package models

import java.util.Date

/**
 * Thumbnails for datasets and files.
 */
case class Thumbnail(
  id: UUID = UUID.generate(),
  chunkSize: Long,
  path: Option[String] = None,
  length: Long,
  md5: Option[String] = None,
  filename: Option[String] = None,
  contentType: String,
  uploadDate: Date
)
