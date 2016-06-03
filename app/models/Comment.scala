package models

import java.util.Date
import com.novus.salat.annotations.raw.Ignore

/**
 * Comment
 *
 * Based on http://docs.mongodb.org/manual/use-cases/storing-comments/
 *
 *
 */
case class Comment(
  author: MiniUser,
  text: String,
  comment_id: Option[UUID] = None,
  dataset_id: Option[UUID] = None,
  file_id: Option[UUID] = None,
  section_id: Option[UUID] = None,
  posted: Date = new Date(),
  id: UUID = UUID.generate,
  @Ignore replies: List[Comment] = List.empty)

