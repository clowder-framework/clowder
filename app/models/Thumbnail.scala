package models

import java.util.Date

/**
 * Thumbnails for datasets and files.
 */
case class Thumbnail(
  id: UUID = UUID.generate(),
  loader_id: String = "",
  loader: String = "",
  length: Long,
  filename: Option[String] = None,
  contentType: String,
  uploadDate: Date
)
