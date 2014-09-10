package controllers

import play.api.mvc.Request

object Utils {
  /**
   * Return base url given a request. This will add http or https to the front, for example
   * https://localhost:9443 will be returned if it is using https.
   */
  def baseUrl(request: Request[Any]) = {
    val httpsPort = System.getProperties().getProperty("https.port", "")
    val protocol = if (httpsPort == request.host.split(':').last)  "https" else "http"
    protocol + "://" + request.host
  }

  /**
   * Returns protocol in request stripping it of the : trailing character.
   * @param request
   * @return
   */
  def protocol(request: Request[Any]) = {
    val httpsPort = System.getProperties().getProperty("https.port", "")
    if (httpsPort == request.host.split(':').last)  "https" else "http"
  }
}