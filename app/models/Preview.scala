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
  collection_id: Option[UUID] = None,
  vocabulary_id: Option[UUID] = None,
  filename: Option[String] = None,
  contentType: String,
  preview_type: Option[String] = None,
  annotations: List[ThreeDAnnotation] = List.empty,
  length: Long,
  extractor_id: Option[String] = None,
  iipURL: Option[String] = None,
  iipImage: Option[String] = None,
  iipKey: Option[String] = None,
  metadataCount: Long = 0,
  @deprecated("use Metadata","since the use of jsonld") jsonldMetadata : List[Metadata]= List.empty
  )
