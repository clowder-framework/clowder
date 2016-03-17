package models

/**
 * Feature vectors used for multimedia indexing.
 *
 */
case class MultimediaFeatures(
  id: UUID = UUID.generate,
  file_id: Option[UUID] = None,
  section_id: Option[UUID] = None,
  features: List[Feature])

case class Feature(
  representation: String,
  descriptor: List[Double])

case class MultimediaDistance(
  source_section: UUID,
  target_section: UUID,
  representation: String,
  distance: Double)

