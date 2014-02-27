package models

import org.bson.types.ObjectId

/**
 * Feature vectors used for multimedia indexing.
 *
 * @author Luigi Marini
 */
case class MultimediaFeatures(
  id: ObjectId = new ObjectId,
  file_id: Option[ObjectId] = None,
  section_id: Option[ObjectId] = None,
  features: List[Feature])

case class Feature(
  representation: String,
  descriptor: List[Double])

