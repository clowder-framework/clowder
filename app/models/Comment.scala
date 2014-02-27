package models

import java.util.Date
import com.mongodb.casbah.Imports._
import securesocial.core.Identity
import com.novus.salat.annotations.raw.Ignore

/**
 * Comment
 *
 * Based on http://docs.mongodb.org/manual/use-cases/storing-comments/
 *
 * @author Rob Kooper
 *
 */
case class Comment(
  author: Identity,
  text: String,
  comment_id: Option[String] = None,
  dataset_id: Option[String] = None,
  file_id: Option[String] = None,
  section_id: Option[String] = None,
  posted: Date = new Date(),
  id: ObjectId = new ObjectId,
  @Ignore replies: List[Comment] = List.empty)

