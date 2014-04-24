package models

import com.mongodb.casbah.Imports._

/**
 * Preview bytes and metadata.
 *
 * @author Luigi Marini
 */
case class Preview(
  id: UUID = UUID.generate,
  file_id: Option[UUID] = None,
  section_id: Option[UUID] = None,
  dataset_id: Option[UUID] = None,
  filename: Option[String] = None,
  contentType: String,
  annotations: List[ThreeDAnnotation] = List.empty,
  length: Long,
  extractor_id: Option[UUID] = None,
  iipURL: Option[String] = None,
  iipImage: Option[String] = None,
  iipKey: Option[String] = None)

