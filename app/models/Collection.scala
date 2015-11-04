package models

import java.util.Date
import securesocial.core.Identity

case class Collection(
  id: UUID = UUID.generate,
  name: String = "N/A",
  author: Identity,
  description: String = "N/A",
  created: Date,
  datasets: List[Dataset] = List.empty,
  thumbnail_id: Option[String] = None,
  previews: List[Preview] = List.empty,
  spaces: List[UUID] = List.empty,
  lastModifiedDate: Date = new Date(),
  followers: List[UUID] = List.empty)
