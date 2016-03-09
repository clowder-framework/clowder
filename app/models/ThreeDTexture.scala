package models

/**
 * 3D textures for x3dom generated from obj models.
 *
 * @author Constantinos Sophocleous
 */
case class ThreeDTexture(
  id: UUID = UUID.generate(),
  loader_id: String = "",
  loader: String = "",
  file_id: Option[String] = None,
  filename: Option[String] = None,
  contentType: String,
  length: Long)

