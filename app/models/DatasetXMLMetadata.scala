package models

/**
 * Dataset XML metadata.
 *
 */
case class DatasetXMLMetadata(
  xmlMetadata: Map[String, Any] = Map.empty,
  fileId: String)

