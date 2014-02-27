package models

import org.bson.types.ObjectId

/**
 * Pyramid tiles of images for Seadragon.
 *
 * @author Constantinos Sophocleous
 *
 */
case class Tile(
  id: ObjectId = new ObjectId,
  preview_id: Option[String] = None,
  filename: Option[String] = None,
  contentType: String,
  level: Option[String],
  length: Long)
