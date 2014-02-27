package models

import org.bson.types.ObjectId

/**
 * Thumbnails for datasets and files.
 */
case class Thumbnail(
id: ObjectId = new ObjectId,
filename: Option[String] = None,
contentType: String,
length: Long)