package models

import java.util.Date

/**
 * Temporary files used when uploading query images for image based searches.
 *
 * TODO change name of collection to be more generic so it can be reused in other places?
 */
case class TempFile(
  id: UUID = UUID.generate,
  path: Option[String] = None,
  filename: String,
  uploadDate: Date,
  contentType: String,
  length: Long = 0)

