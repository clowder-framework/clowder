package models

/**
 * Pyramid tiles of images for Seadragon.
 *
 *
 */
case class Tile(
  id: UUID = UUID.generate,
  preview_id: Option[String] = None,
  filename: Option[String] = None,
  contentType: String,
  level: Option[String],
  length: Long)
