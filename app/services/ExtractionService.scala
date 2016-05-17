package services

import models.{UUID, Extraction,WebPageResource}
import java.util.Date


/**
 * Track information about individual extractions.
 *
 */
trait ExtractionService {

  def findIfBeingProcessed(fileId: UUID): Boolean

  def findAll(): List[Extraction]

  def findByFileId(fileId: UUID): List[Extraction]

  def insert(extraction: Extraction)
  
  def getExtractorList(fileId:UUID): collection.mutable.Map[String,String]
  
  def getExtractionTime(fileId:UUID): List[Date]
  
  def save(webpr: WebPageResource): UUID
  
  def getWebPageResource(id: UUID): Map[String,String]
}
