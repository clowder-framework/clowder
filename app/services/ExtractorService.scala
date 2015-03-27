package services

import models._
import play.api.libs.json.JsValue

/**
 * Keeps track of extractors status information
 */

trait ExtractorService {
  
  def getExtractorServerIPList(): List[String]
  
  def getExtractorNames(): List[String]
  
  def getExtractorInputTypes(): List[String]
  
  def insertServerIPs(l: List[String])
  
  def insertExtractorNames(names: List[String])
  
  def insertInputTypes(inputTypes: List[String])
  
  //---Temporary fix for BD-289
  def insertExtractorDetail(extractorTuple: List[ExtractorDetail])
  
  def getExtractorDetail(): Option[JsValue]
 //--End of Temporary fix BD-289
  
  def dropAllExtractorStatusCollection()  
}