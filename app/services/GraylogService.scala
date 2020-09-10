package services

import services.LogService

/**
 * Service to get extraction logs from Graylog.
 */

class GraylogService (serviceEndpoint: String) extends LogService {
  def getLog(extractorName: String, submissionID: String): String = {
    return "hello"
  }
}