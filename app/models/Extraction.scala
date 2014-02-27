package models

import org.bson.types.ObjectId
import java.util.Date

/**
 * Status of extraction job.
 *
 * @author Luigi Marini
 *
 */
case class Extraction(
  id: ObjectId = new ObjectId,
  file_id: ObjectId,
  extractor_id: String,
  status: String = "N/A",
  start: Option[Date],
  end: Option[Date])
