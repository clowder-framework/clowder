package api

import java.net.URL

import com.ning.http.client.Realm.AuthScheme
import javax.inject.Inject
import play.api.Logger
import play.api.libs.concurrent.Execution.Implicits.defaultContext
import play.api.libs.iteratee._
import play.api.libs.ws.WS.WSRequestHolder
import play.api.libs.ws._
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
    // Parse basic auth credentials from target URL
    val targetUrl: URL = new URL(proxyTarget);
    val userInfo = targetUrl.getUserInfo()
    val sanitizedUrl = proxyTarget.replaceAll(userInfo + "@", "")

    // If we find a username/password, scrape them out of the target URL
    val username = if (null != userInfo) { userInfo.split(":").apply(0) } else { "" }
    val password = if (null != userInfo) { userInfo.split(":").apply(1) } else { "" }

    val initialReq = WS.url(sanitizedUrl)
        .withQueryString(originalRequest.queryString.mapValues(_.head).toSeq: _*)
        .withFollowRedirects(true)

    // If original request had Content-Type/Length headers, copy them to the proxied request
    val contentType = originalRequest.headers.get("Content-Type").orNull
    val contentLength = originalRequest.headers.get("Content-Length").orNull
    val reqWithContentHeaders = if (contentType != null && contentLength != null) {
      initialReq.withHeaders(CONTENT_TYPE -> contentType, CONTENT_LENGTH -> contentLength)
    } else {
      initialReq
    }

    // If original request had a Cookie header, copy it to the proxied request
    val cookies = originalRequest.headers.get("Cookie").orNull
    val reqWithHeaders = if (cookies != null) {
      reqWithContentHeaders.withHeaders(COOKIE -> cookies)
    } else {
      reqWithContentHeaders
    }

    // If the configured URL contained service account credentials(UserInfo), copy it to the proxied request
    if (!username.isEmpty && !password.isEmpty) {
      Logger.debug(s"PROXY :: Using service account credentials - $username")
      return reqWithHeaders.withAuth(username, password, AuthScheme.BASIC)
    } else {
      Logger.debug("PROXY :: No credentials specified")
      return reqWithHeaders
    }
  }

  /**
    * Copies the header values from our intermediary (proxied) response to the
    * SimpleResult that we will return to the caller of the proxy API
    */
  def buildProxiedResponse(lastResponse: Response, proxiedResponse: SimpleResult): SimpleResult = {
    // TODO: other response headers my be needed for specific cases
    val chunkedResponse = proxiedResponse.withHeaders (
      //CONNECTION -> lastResponse.header("Connection").orNull,
      //SERVER -> lastResponse.header("Server").orNull,

      // Always chunk the response (for simplicity, so that we don't need to calculate/specify the Content-Length)
      TRANSFER_ENCODING -> "chunked"
    )

    // Default Content-Disposition to a sensible value if it is not present
    // TODO: test cases seemed to pass consistently, but is "inline" the best default here?
    // TODO: Do ANY of these headers have sensible/noop defaults? Would this even be this a good idea?
    val contentDisposition = lastResponse.header ("Content-Disposition").orNull
    contentDisposition match {
      case null | "" => return chunkedResponse
      case _ => return chunkedResponse.withHeaders(CONTENT_DISPOSITION -> contentDisposition)
    }
  }

  /**
    * Given a response, chunk its body and return/forward it as a SimpleResult
    */
  def chunkAndForwardResponse(originalResponse: Response): SimpleResult = {
    val statusCode = originalResponse.getAHCResponse.getStatusCode
    if (statusCode >= 400) {
      Logger.error("PROXY :: " + statusCode + " - " + originalResponse.getAHCResponse.getStatusText)
    }

    // Chunk the response
    val bodyStream = originalResponse.ahcResponse.getResponseBodyAsStream
    val bodyEnumerator = Enumerator.fromStream(bodyStream)
    val payload = Status(statusCode).chunked(bodyEnumerator)

    // Return a SimpleResult, coerced into our desired Content-Type
    val contentType = originalResponse.header("Content-Type").getOrElse("text/plain")
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
      proxiedRequest.get.map { originalResponse => chunkAndForwardResponse(originalResponse) }
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
      proxiedRequest.delete.map { originalResponse => chunkAndForwardResponse(originalResponse) }
    }
  }
}
