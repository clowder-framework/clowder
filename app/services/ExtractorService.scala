package services

import models._
import play.api.libs.json.JsValue

/**
 * Keeps track of extractors status information
 */

trait ExtractorService {
  
  def getExtractorServerIPList(): List[String]

  def disableAllExtractors(): Boolean

  def getEnabledExtractors(): List[String]

  def enableExtractor(extractor: String)

  def getExtractorNames(categories: List[String]): List[String]
  
  def getExtractorInputTypes(): List[String]
  
  def insertServerIPs(l: List[String])
  
  def insertExtractorNames(names: List[String])
  
  def insertInputTypes(inputTypes: List[String])
  
  //---Temporary fix for BD-289
  def insertExtractorDetail(extractorTuple: List[ExtractorDetail])
  
  def getExtractorDetail(): Option[JsValue]
 //--End of Temporary fix BD-289
  
  def dropAllExtractorStatusCollection()

  def listExtractorsInfo(categories: List[String]): List[ExtractorInfo]

  def getExtractorInfo(extractorName: String): Option[ExtractorInfo]

  def updateExtractorInfo(e: ExtractorInfo): Option[ExtractorInfo]

  def deleteExtractor(extractorName: String)
}