package services

import services.LogService
import javax.inject.{Inject, Singleton}

/**
 * Service to get extraction logs from Graylog.
 */
@Singleton
class GraylogService @Inject() (serviceEndpoint: String) extends LogService {
  def getLog(extractorName: String, submissionID: String): List[String] = {
    return "hello"
  }
}