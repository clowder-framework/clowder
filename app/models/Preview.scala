package models

import com.mongodb.casbah.Imports._

/**
 * Preview bytes and metadata.
 *
 * @author Luigi Marini
 */
case class Preview(
  id: ObjectId = new ObjectId,
  file_id: Option[String] = None,
  section_id: Option[String] = None,
  dataset_id: Option[String] = None,
  filename: Option[String] = None,
  contentType: String,
  annotations: List[ThreeDAnnotation] = List.empty,
  length: Long,
  extractor_id: Option[String] = None,
  iipURL: Option[String] = None,
  iipImage: Option[String] = None,
  iipKey: Option[String] = None)





