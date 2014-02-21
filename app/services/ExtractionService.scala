package services

/**
 * Track information about individual extractions.
 *
 * Created by lmarini on 2/21/14.
 */
trait ExtractionService {

  def findIfBeingProcessed(fileId: String): Boolean
}
