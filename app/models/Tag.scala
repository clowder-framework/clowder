package models

import java.util.Date

import org.bson.types.ObjectId

/**
 * Add and remove tags
 *
 * @author Luigi Marini
 */
case class Tag(
  id: ObjectId = new ObjectId,
  name: String,
  userId: Option[String],
  extractor_id: Option[String],
  created: Date)

