package models

/**
 * Dataset XML metadata.
 *
 * @author Constantinos Sophocleous
 */
case class DatasetXMLMetadata(
  xmlMetadata: Map[String, Any] = Map.empty,
  fileId: String)

