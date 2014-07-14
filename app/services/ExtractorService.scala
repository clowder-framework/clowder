package services

/**
 * Keeps track of extractors status information
 */

trait ExtractorService {
  
  def getExtractorServerIPList(): List[String]
  
  def getExtractorNames() : List[String]
  
  def getExtractorInputTypes() :  List[String]
  
  def insertServerIPs(l:List[String])
  
  def insertExtractorNames(names:List[String])
  
  def insertInputTypes(inputTypes:List[String])

}