package services

import models.Extraction

/**
 * Track information about individual extractions.
 *
 * Created by lmarini on 2/21/14.
 */
trait ExtractionService {

  def findIfBeingProcessed(fileId: String): Boolean

  def findAll(): List[Extraction]

  def insert(extraction: Extraction)
}
