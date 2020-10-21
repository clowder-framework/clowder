package services

import java.io.InputStream
import java.net.URLEncoder

import jsonutils.JsonUtil
import play.api.libs.json._
import play.api.libs.json.Json._

import play.api.Logger
import play.api.libs.ws.WS.WSRequestHolder
import play.api.libs.ws._

import javax.inject.{Inject}
import scala.collection.immutable.List
import com.ning.http.client.Realm.AuthScheme

import scala.collection.JavaConversions._
import scala.concurrent.Await
import scala.concurrent.duration.Duration

/**
 * Service to get extraction logs from Graylog.
 */
class GraylogService @Inject() extends LogService {
  def getLog(extractorName: String, submissionID: Option[String]): List[String] = {
    val playConfig = play.Play.application().configuration()
    val serviceEndpoint = playConfig.getString("clowder.log.serviceEndpoint")
    val username = playConfig.getString("clowder.log.username")
    val password = playConfig.getString("clowder.log.password")
    val prefix = playConfig.getString("clowder.log.extractorNamePrefix")
    val extractorRename: String = extractorName.replaceAll("[.]", "_")
    val query: String = "application_name:" + prefix + extractorRename + "*"
    val queryEncode: String = URLEncoder.encode(query, "UTF-8")
    val queryUrl: String = serviceEndpoint + "/api/search/universal/relative?query=" + queryEncode
    Logger.debug(s"queryUrl - $queryUrl")
    var logs: List[String] = List()
    implicit val context = scala.concurrent.ExecutionContext.Implicits.global
    val result = WS.url(queryUrl)
      .withHeaders("X-Requested-By" -> "cli")
      .withHeaders("Accept" -> "application/json")
      .withHeaders("Content-Type" -> "application/json")
      .withAuth(username, password, AuthScheme.BASIC)
      .get().map{
      response => response.status match {
        case 200 => {
          val responseJson = Json.stringify(response.json)
          val responseJsonValue = JsonUtil.parseJSON(responseJson).asInstanceOf[java.util.LinkedHashMap[String, java.util.LinkedList[java.util.LinkedHashMap[String, String]]]]
          val messagesjsonValue = responseJsonValue.get("messages")
          messagesjsonValue.foreach(elem => {
            val elemMap = elem.asInstanceOf[java.util.HashMap[String, java.util.HashMap[String, String]]]
            var message: String = elemMap.get("message").get("message")

            val secretKeyIndex: Int = message.indexOf("'secretKey':")
            val secretKeyLen: Int = "'secretKey':".length()
            if(secretKeyIndex >= 0) {
              val prev: String = message.substring(0, secretKeyIndex)
              val sub: String = message.substring(secretKeyIndex+secretKeyLen)
              val secretValueIndex: Int = sub.indexOf("',")
              val suffix: String = sub.substring(secretValueIndex+"',".length())
              message = prev+suffix
            }

            val keyIndex: Int = message.indexOf("key=")
            keyIndex match {
              case -1 => {
                logs = logs :+ message
              }
              case _  => {
                val filterMessage: String = message.substring(0, keyIndex)
                logs = logs :+ filterMessage
              }
            }
          }
          )
        }
        case _ => {
          val status = response.status
          Logger.warn(s"Problem accessing api, status '$status'")
        }
      }
    }

    Await.result(result, Duration.Inf)
    return logs
  }
}
