package models

import com.mongodb.casbah.Imports._

/**
 * 3D textures for x3dom generated from obj models.
 *
 * @author Constantinos Sophocleous
 */
case class ThreeDAnnotation(
  x_coord: String,
  y_coord: String,
  z_coord: String,
  description: String = "",
  id: ObjectId = new ObjectId)