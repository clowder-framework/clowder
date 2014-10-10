package models

import java.util.Date

case class Collection(
  id: UUID = UUID.generate,
  name: String = "N/A",
  description: String = "N/A",
  created: Date,
  datasets: List[Dataset] = List.empty,
  thumbnail_id: Option[String] = None,
  previews: List[Preview] = List.empty)

