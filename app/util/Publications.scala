package util

import play.api.Play._
import play.api.Logger
import play.api.libs.json._
import play.api.libs.json.Json._
import scala.concurrent.{ Future, Await }
import play.api.mvc.{ MultipartFormData, Request, Action, Results }
import play.api.libs.ws._
import scala.concurrent.duration._
import models._
import services.SpaceService

/**
 * Utility to get publications from sead services.
 *
 */
object Publications {
      
      
    def getPublications(space: String, spaces: SpaceService) = {
    implicit val context = scala.concurrent.ExecutionContext.Implicits.global
    val endpoint = play.Play.application().configuration().getString("publishData.list.uri").replaceAll("/$", "")
    Logger.debug(endpoint)
    val futureResponse = WS.url(endpoint).get()
    var publishDataList: List[Map[String, String]] = List.empty
     Logger.warn("Space is " + space)
    val spaceSet: Set[String] = space match {
      case s: String if (s.isEmpty) => Set()
      case sp: String => spaces.get(UUID(sp)) match {
        case Some(s) => ( s.name ::  s.affiliatedSpaces).toSet 
        case None => Set()
      }
      
    }
    val result = futureResponse.map {
      case response =>
        if (response.status >= 200 && response.status < 300 || response.status == 304) {
          Logger.warn("Size is " + spaceSet.size)
          val rawDataList = spaceSet.size match {
            case 0 => response.json.as[List[JsValue]]
            case _ => {
              response.json.as[List[JsValue]].filter(x => {
                Logger.warn(spaceSet.toString())
                val containsName = ((x.as[JsObject]) \ "Publishing Project Name").asOpt[String] match {
                  case Some(s) => spaceSet.contains(s)
                  case None => false
                }
                val containsProject = spaceSet.contains(((x.as[JsObject]) \ "Publishing Project").as[String])
                containsName || containsProject
              })
            }
          }

          rawDataList.reverse
        } else {
          Logger.error("Error Getting published data: " + response.getAHCResponse.getResponseBody)
          List.empty
        }
    }

    Await.result(result, Duration.Inf)

  }

}