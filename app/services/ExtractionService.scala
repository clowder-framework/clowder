package services

import models._
import java.util.Date

import org.bson.types.ObjectId


/**
 * Track information about individual extractions.
 *
 */
trait ExtractionService {

  def findIfBeingProcessed(fileId: UUID): Boolean

  def findAll(max: Int = 100): List[Extraction]

  def get(msgId: UUID): Option[Extraction]

  def findById(resource: ResourceRef): List[Extraction]

  def findByExtractorIDBefore(extractorID: String, status: String, date: String, limit: Int): List[Extraction]

  def insert(extraction: Extraction): Option[ObjectId]
  
  def getExtractorList(fileId:UUID): collection.mutable.Map[String,String]
  
  def getExtractionTime(fileId:UUID): List[Date]
  
  def save(webpr: WebPageResource): UUID
  
  def getWebPageResource(id: UUID): Map[String,String]

  def groupByType(extraction_list: List[Extraction]): Map[String, ExtractionGroup]
}
