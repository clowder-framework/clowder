package models

import com.mongodb.casbah.Imports._
import java.util.Date
import securesocial.core.Identity

/**
 * A dataset is a collection of files, and streams.
 *
 *
 * @author Luigi Marini
 *
 */
case class Dataset(
  id: UUID = UUID.generate,
  var name: String = "N/A",
  author: Identity,
  var description: String = "N/A",
  created: Date,
  files: List[File] = List.empty,
  streams_id: List[ObjectId] = List.empty,
  tags: List[Tag] = List.empty,
  jsonldMetadata: List[LDMetadata] = List.empty,
  @deprecated("use LDMetadata","since the use of jsonld") metadata: Map[String, Any] = Map.empty,
  @deprecated("use LDMetadata","since the use of jsonld") userMetadata: Map[String, Any] = Map.empty,
  collections: List[String] = List.empty,
  thumbnail_id: Option[String] = None,
  @deprecated("use LDMetadata","since the use of jsonld") datasetXmlMetadata: List[DatasetXMLMetadata] = List.empty,
  @deprecated("use LDMetadata","since the use of jsonld") userMetadataWasModified: Option[Boolean] = None,
  licenseData: LicenseData = new LicenseData(),
  notesHTML: Option[String] = None)

