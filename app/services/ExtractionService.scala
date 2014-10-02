package services

import models.{UUID, Extraction}
import java.util.Date

/**
 * Track information about individual extractions.
 *
 * Created by lmarini on 2/21/14.
 */
trait ExtractionService {

  def findIfBeingProcessed(fileId: UUID): Boolean

  def findAll(): List[Extraction]

  def insert(extraction: Extraction)
  
  def getExtractorList(fileId:UUID):collection.mutable.Map[String,String] 
  
  def getExtractionTime(fileId:UUID):List[Date]
}
