package models

import services.ExtractorService
import services.DI
import services.ExtractionRequestsService

/**
 *  DTS extractions information
 */

object ExtractionInfoSetUp {
  val extractors: ExtractorService =  DI.injector.getInstance(classOf[ExtractorService])
  val dtsrequests:ExtractionRequestsService=DI.injector.getInstance(classOf[ExtractionRequestsService])

  /*
   * Updates DTS extraction request
   *
   */
  def updateDTSRequests(file_id:UUID,extractor_id:String)={

    dtsrequests.updateRequest(file_id,extractor_id)
  }
}
