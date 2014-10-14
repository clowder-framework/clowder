package services

/**
 * Keeps track of extractors status information
 */
import models._
import play.api.libs.json.JsValue
trait ExtractorService {
  
  def getExtractorServerIPList(): List[String]
  
  def getExtractorNames() : List[String]
  
  def getExtractorInputTypes() :  List[String]
  
  def insertServerIPs(l:List[String])
  
  def insertExtractorNames(names:List[String])
  
  def insertInputTypes(inputTypes:List[String])
  
  //---Temporary fix
  def insertExtractorDetail(extractorTuple:List[ExtractorDetail])
  
  def getExtractorDetail():  Option[JsValue]
//--End of Temporary fix
}