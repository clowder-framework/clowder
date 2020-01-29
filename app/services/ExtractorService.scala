package services

import models._
import play.api.libs.json.JsValue

/**
 * Keeps track of extractors status information
 */

trait ExtractorService {

  def disableAllExtractors(): Boolean

  def getEnabledExtractors(): List[String]

  def enableExtractor(extractor: String)

  def getExtractorNames(categories: List[String]): List[String]
  
  def getExtractorInputTypes(): List[String]

  def listExtractorsInfo(categories: List[String]): List[ExtractorInfo]

  def getExtractorInfo(extractorName: String): Option[ExtractorInfo]

  def updateExtractorInfo(e: ExtractorInfo): Option[ExtractorInfo]
}