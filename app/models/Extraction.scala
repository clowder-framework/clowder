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

 /**
  *  Currently running extractor name
  */ 

case class ExtractorNames(
   name:String=""    
)

/**
 * An input type supported by an extractor
 */
case class ExtractorInputType (
inputType:String=""    
)

/**
 * Servers information running different extractors
 * and supported file formats
 * 
 */
case class ExtractorServer(
server: String="N/A"
)

/**
 * Extractors' Servers IPs, Name and Count
 * This is a temporary fix for keeping track of number of extractors running in different servers
 * This class may be omitted once the design and implementation for BD-289 are done
 */
case class ExtractorDetail(
    ip: String = "",
    name: String = "",
    var count: Int = 0
)


