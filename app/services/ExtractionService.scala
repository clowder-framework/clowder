package services

import models.{UUID, Extraction}

/**
 * Track information about individual extractions.
 *
 * Created by lmarini on 2/21/14.
 */
trait ExtractionService {

  def findIfBeingProcessed(fileId: UUID): Boolean

  def findAll(): List[Extraction]

  def insert(extraction: Extraction)
}
