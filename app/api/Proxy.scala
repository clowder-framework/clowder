package api

import play.api.libs.concurrent.Execution.Implicits.defaultContext
import play.api.libs.iteratee._
import play.api.libs.ws._
import javax.inject.Inject
import play.api.libs.ws.WS.WSRequestHolder
import play.api.mvc.SimpleResult

import scala.concurrent.Future

/**
  * An API that allows you to configure Clowder as a reverse-proxy. Proxy rules can be composed by defining a
  * "clowder.proxy" block in Clowder's configuration (e.g. your custom.conf file), which maps endpoint_keys to their
  * target endpoints. Requests to "/api/proxy/:endpoint_key" will then be routed to the specified target using the
  * per-method code defined in the endpoint configuration below.
  *
  * For example:
  *   clowder.proxy {
  *     google="https://www.google.com"
  *     rabbitmq="http://localhost:15672"
  *   }
  *
  * With the above configured, navigating to /api/proxy/google will proxy your requests to https://www.google.com
  * and transparently send you the response to your proxied request.
  *
  *
  * @author Mike Lambert
  *
  */
object Proxy {
  /** The prefix to search Clowder's configuration for proxy endpoint */
  val ConfigPrefix: String = "clowder.proxy."
}

class Proxy @Inject()() extends ApiController {
  import Proxy.ConfigPrefix

  /**
    * Translates an endpoint_key to a target endpoint based on Clowder's configuration
    */
  def getProxyTarget(endpoint_key: String, pathSuffix: String): String = {
    // Prefix our configuration values
    val endpointCfgKey = ConfigPrefix + endpoint_key
    val playConfig = play.Play.application().configuration()

    // If this endpoint has been configured, return it
    if (playConfig.keys.contains(endpointCfgKey)) {
      val urlBase = playConfig.getString(endpointCfgKey)
      if (null != pathSuffix) {
        return urlBase + "/" + pathSuffix
      }

      // No path suffix given, so just return urlBase
      return urlBase
    }

    // No endpoint configuration found, return null
    return null
  }

  /**
    * Build up our intermediary (proxied) request from the original
    */
  def buildProxiedRequest(proxyTarget: String, originalRequest: UserRequest[String]): WSRequestHolder = {
    return WS.url(proxyTarget)
     .withQueryString(originalRequest.queryString.mapValues(_.head).toSeq: _*)

     // If client return 301 or 302, follow the redirect
     .withFollowRedirects(true)

     // TODO: other request headers my be needed for specific cases
     .withHeaders(
       // FIXME: How do we prevent setting null values for missing headers?
       // FIXME: Sending null/invalid values for headers causes indeterminate results
       //ACCEPT_ENCODING-> originalRequest.headers.get("Accept-Encoding").orNull,
       //ACCEPT -> originalRequest.headers.get("Accept").orNull,
       //ACCEPT_LANGUAGE -> originalRequest.headers.get("Accept-Language").orNull,
       //CONNECTION -> originalRequest.headers.get("Connection").orNull,
       //"Upgrade-Insecure-Requests" -> originalRequest.headers.get("Upgrade-Insecure-Requests").orNull,
       //HOST -> originalRequest.headers.get("Host").orNull,
       //"DNT" -> originalRequest.headers.get("DNT").orNull,
       //CACHE_CONTROL -> originalRequest.headers.get("Cache-Control").orNull,
       //PRAGMA -> originalRequest.headers.get("Pragma").orNull,
       //COOKIE -> originalRequest.headers.get("Cookie").orNull
     )
  }

  /**
    * Copies the header values from our intermediary (proxied) response to the
    * SimpleResult that we will return to the caller of the proxy API
    */
  def buildProxiedResponse(lastResponse: Response, proxiedResponse: SimpleResult): SimpleResult = {
    // TODO: other response headers my be needed for specific cases
    return proxiedResponse.withHeaders (
        //CONNECTION -> lastResponse.header("Connection").orNull,
        //SERVER -> lastResponse.header("Server").orNull,
        // Default Content-Disposition to a sensible value if it is not present
        // TODO: test cases seemed to pass consistently, but is "inline" the best default here?
        // TODO: Do ANY of these headers have sensible/noop defaults? Would this even be this a good idea?
        CONTENT_DISPOSITION -> lastResponse.header ("Content-Disposition").getOrElse("inline"),

        // Always chunk the response (for simplicity, so that we don't need to calculate/specify the Content-Length)
        TRANSFER_ENCODING -> "chunked"
      )
  }

  /**
    * Given a response, chunk its body and return/forward it as a SimpleResult
    */
  def chunkAndForwardResponse(originalResponse: Response): SimpleResult = {
    // Copy all of the headers from our proxied response
    val contentType = originalResponse.header("Content-Type").orNull
    val statusCode = originalResponse.getAHCResponse.getStatusCode
    val bodyStream = originalResponse.ahcResponse.getResponseBodyAsStream

    // Chunk the response
    val bodyEnumerator = Enumerator.fromStream(bodyStream)
    val payload = Status(statusCode).chunked(bodyEnumerator)

    // Return a SimpleResult, coerced into our desired Content-Type
    return buildProxiedResponse(originalResponse, payload).as(contentType)
  }

  /**
    * Perform a GET request to the specified endpoint_key on the user's behalf
    */
  // Attempt to use parse.tolerantText to make this type-agnostic
  def get(endpoint_key: String, pathSuffix: String)= AuthenticatedAction.async(parse.tolerantText) { implicit request =>
    // Read Clowder configuration to retrieve the target endpoint URL
    val proxyTarget = getProxyTarget(endpoint_key, pathSuffix)

    if (null == proxyTarget) {
       Future(NotFound("Not found: " + endpoint_key))
    } else {
      // Build our proxied request (preserve query string parameters)
      val proxiedRequest = buildProxiedRequest(proxyTarget, request)

      // Proxy a GET request, then chunk and return the response
      proxiedRequest.get().map { originalResponse => chunkAndForwardResponse(originalResponse) }
    }
  }

  /**
    * Perform a POST request to the specified endpoint_key on the user's behalf
    */
  // Attempt to use parse.tolerantText to make this type-agnostic
  def post(endpoint_key: String, pathSuffix: String)= AuthenticatedAction.async(parse.tolerantText) { implicit request =>
    // Read Clowder configuration to retrieve the target endpoint URL
    val proxyTarget = getProxyTarget(endpoint_key, pathSuffix)

    if (null == proxyTarget) {
      Future(NotFound("Not found: " + endpoint_key))
    } else {
      // Build our proxied request (preserve query string parameters)
      val proxiedRequest = buildProxiedRequest(proxyTarget, request)

      // Proxy a POST request, then chunk and return the response
      proxiedRequest.post(request.body).map { originalResponse => chunkAndForwardResponse(originalResponse) }
    }
  }

  /**
    * Perform a PUT request to the specified endpoint_key on the user's behalf
    */
  // Attempt to use parse.tolerantText to make this type-agnostic
  def put(endpoint_key: String, pathSuffix: String) = AuthenticatedAction.async(parse.tolerantText) { implicit request =>
    // Read Clowder configuration to retrieve the target endpoint URL
    val proxyTarget = getProxyTarget(endpoint_key, pathSuffix)

    if (null == proxyTarget) {
      Future(NotFound("Not found: " + endpoint_key))
    } else {
      // Build our proxied request (preserve query string parameters)
      val proxiedRequest = buildProxiedRequest(proxyTarget, request)

      // Proxy a PUT request, then chunk and return the response
      proxiedRequest.put(request.body).map { originalResponse => chunkAndForwardResponse(originalResponse) }
    }
  }

  /**
    * Perform a DELETE request to the specified endpoint_key on the user's behalf
    */
  // Attempt to use parse.tolerantText to make this type-agnostic
  def delete(endpoint_key: String, pathSuffix: String) = AuthenticatedAction.async(parse.tolerantText) { implicit request =>
    // Read Clowder configuration to retrieve the target endpoint URL
    val proxyTarget = getProxyTarget(endpoint_key, pathSuffix)

    if (null == proxyTarget) {
      Future(NotFound("Not found: " + endpoint_key))
    } else {
      // Build our proxied request (preserve query string parameters)
      val proxiedRequest = buildProxiedRequest(proxyTarget, request)

      // Proxy a DELETE request, then chunk and return the response
      proxiedRequest.delete().map { originalResponse => chunkAndForwardResponse(originalResponse) }
    }
  }
}
