package models

/**
 * 3D binary geometry files for x3dom.
 *
 * @author Constantinos Sophocleous
 *
 */
case class ThreeDGeometry(
  id: UUID = UUID.generate,
  file_id: Option[String] = None,
  filename: Option[String] = None,
  contentType: String,
  level: Option[String],
  length: Long)

