package models

import java.util.Date

import com.mongodb.casbah.Imports._
import securesocial.core.Identity

/**
 * Created by yanzhao3 on 8/12/15.
 */
case class CurationObj (
  id: UUID = UUID.generate,
  name: String = "N/A",
  author: Option[Identity],
  description: String = "N/A",
  created: Date,
  spaces: UUID,
  dataset: Option[CurationDataset],
  collection: Option[CurationCollection]
)

case class CurationDataset(
  id: UUID,
  name: String,
  author: Identity,
  description: String = "N/A",
  created: Date,
  files: List[File] = List.empty,
  streams_id: List[ObjectId] = List.empty,
  tags: List[Tag] = List.empty,
  metadata: Map[String, Any] = Map.empty,
  userMetadata: Map[String, Any] = Map.empty,
  collections: List[String] = List.empty,
  thumbnail_id: Option[String] = None,
  datasetXmlMetadata: List[DatasetXMLMetadata] = List.empty,
  userMetadataWasModified: Option[Boolean] = None,
  licenseData: LicenseData = new LicenseData())

case class CurationCollection(
  id: UUID = UUID.generate,
  name: String,
  author: Identity,
  description: String = "N/A",
  created: Date,
  datasets: List[Dataset] = List.empty,
  thumbnail_id: Option[String] = None)



