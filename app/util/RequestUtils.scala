package util

import java.net.URL
import controllers.Utils
import play.api.mvc.Request

/**
 * Utility functions for retrieving desired information from requests
 */
object RequestUtils {

  /**
   * Extracts resource URL details from request
   */
  def getBaseUrlAndProtocol(request: Request[Any], isContextRequired: Boolean = false): (String, Boolean) = {

    val isHttps = controllers.Utils.https(request) // Check whether the request is http or https
    val baseUrl = new URL(Utils.baseUrl(request)) // Get base URL from request

    // Extract relevant parts from URL
    val baseUrlExcludingContext = if (!isContextRequired) {
      if (baseUrl.getPort == -1) {
        baseUrl.getHost
      } else {
        baseUrl.getHost + ":" + baseUrl.getPort
      }
    }
    else {
      baseUrl.toString
    }

    (baseUrlExcludingContext, isHttps)
  }
}
