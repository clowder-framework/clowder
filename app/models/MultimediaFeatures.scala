package models

/**
 * Feature vectors used for multimedia indexing.
 *
 * @author Luigi Marini
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
  distance: Double,
  source_spaces: List[UUID])

