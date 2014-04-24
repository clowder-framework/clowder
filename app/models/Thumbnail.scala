package models

/**
 * Thumbnails for datasets and files.
 */
case class Thumbnail(
id: UUID = UUID.generate,
filename: Option[String] = None,
contentType: String,
length: Long)
