package models

import java.util.Date

/**
 * Add and remove tags
 *
 */
case class Tag(
  id: UUID = UUID.generate,
  name: String,
  userId: Option[String],
  extractor_id: Option[String],
  created: Date)

object Tag {
  implicit def tagToElasticSearchTag(t: Tag): ElasticSearchTag = {
    new ElasticSearchTag(
      t.userId.getOrElse(""),
      t.created,
      t.name
    )
  }
}
