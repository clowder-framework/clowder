package services
/**
 * Service to get extraction logs.
 */
trait LogService {
  def getLog(extractorName: String, submissionID: String): String
}