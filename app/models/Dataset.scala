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
  name: String = "N/A",
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
  notesHTML: Option[String] = None 
)