package models

import com.mongodb.casbah.Imports._

/**
 * Preview bytes and metadata.
 *
 */

case class Preview(
  id: UUID = UUID.generate,
  loader_id: String = "",
  loader: String = "",
  file_id: Option[UUID] = None,
  section_id: Option[UUID] = None,
  dataset_id: Option[UUID] = None,
  collection_id: Option[UUID] = None,
  filename: Option[String] = None,
  contentType: String,
  preview_type: Option[String] = None,
  title: Option[String] = None,
  length: Long,
  extractor_id: Option[String] = None,
  metadataCount: Long = 0,
  @deprecated("use Metadata","since the use of jsonld") jsonldMetadata : List[Metadata]= List.empty
  )
