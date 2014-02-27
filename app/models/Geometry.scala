package models

import org.bson.types.ObjectId

/**
 * 3D binary geometry files for x3dom.
 *
 * @author Constantinos Sophocleous
 *
 */
case class ThreeDGeometry(
  id: ObjectId = new ObjectId,
  file_id: Option[String] = None,
  filename: Option[String] = None,
  contentType: String,
  level: Option[String],
  length: Long)

