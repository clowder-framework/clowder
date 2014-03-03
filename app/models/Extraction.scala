package models

import java.util.Date

/**
 * Status of extraction job.
 *
 * @author Luigi Marini
 *
 */
case class Extraction(
  id: UUID = UUID.generate,
  file_id: UUID,
  extractor_id: String,
  status: String = "N/A",
  start: Option[Date],
  end: Option[Date])
