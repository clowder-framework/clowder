package models

/**
 * 3D textures for x3dom generated from obj models.
 *
 */
case class ThreeDTexture(
  id: UUID = UUID.generate,
  file_id: Option[String] = None,
  filename: Option[String] = None,
  contentType: String,
  length: Long)

