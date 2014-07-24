package models

import java.util.Date

/**
 * Add and remove tags
 *
 * @author Luigi Marini
 */
case class Tag(
  id: UUID = UUID.generate,
  name: String,
  userId: Option[String],
  extractor_id: Option[String],
  created: Date)


