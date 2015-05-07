package controllers

import javax.inject.Inject

import services.EventService
import models.Event
import models.UUID
import play.Logger
import play.mvc.Call

/**
 * Created by maxyuan on 4/27/15.
 */
class RSS @Inject() (events: EventService) extends SecuredController {
  val defaultNumberOfItems = 10
  val acceptableTypes = List("file", "dataset", "collection", "user")

  def getLinkForObj(obj_type: String, obj_id: Option[UUID]) = {
    obj_type match {
      case "file" => {
        routes.Files.file(obj_id.get)
      }
      case "dataset" => {
        routes.Datasets.dataset(obj_id.get)
      }
      case "collection" => {
        routes.Collections.collection(obj_id.get)
      }
      case "user" => {
        routes.Profile.viewProfileUUID(obj_id.get)
      }
      case _ => {
        routes.Application.index()
      }
    }
  }

  def getItemLink(item: Event) = {
    var event_type_split = item.event_type.split("_")
    event_type_split.length match {
      case 2 => {
        if (event_type_split(0) == "comment") {
          getLinkForObj("file", item.source_id)
        } else {
          getLinkForObj(event_type_split(1), item.object_id)
        }
      }
      case 3 => {
        if (item.event_type.indexOf("tag") >= 0) {
          getLinkForObj(event_type_split(2), item.object_id)
        } else {
          getLinkForObj(event_type_split(2), item.source_id)
        }
      }
    }
  }

  def siteRSS(n: Option[Int]) = SecuredAction() { implicit request =>
    implicit val user = request.user

    val itemsToGet = n.getOrElse(defaultNumberOfItems)
    val feedItems = events.getLatestNEventsOfType(itemsToGet, None)
    val rss =
      <rss version="2.0">
        <channel>
          <title>Medici RSS feed</title>
          <description>The latest events happening in Medici</description>
          <link>{routes.Application.index().absoluteURL()}</link>
          {
            for (item <- feedItems) yield {
              <item>
                <title>Event</title>
                <description>{item.user.fullName}{views.html.newsFeedCardEmail(item)}</description>
                <link>{getItemLink(item).absoluteURL()}</link>
              </item>
            }
          }
        </channel>
      </rss>
    Ok(rss)
  }

  def siteRSSOfType(n: Option[Int], event_type: String) = SecuredAction() { implicit request =>
    implicit val user = request.user

    val itemsToGet = n.getOrElse(defaultNumberOfItems)
    var feedItems = List[Event]()
    if (!acceptableTypes.contains(event_type)) {
      feedItems = events.getLatestNEventsOfType(itemsToGet, None)
    } else {
      feedItems = events.getLatestNEventsOfType(itemsToGet, Option(event_type))
    }
    Ok(
      <rss version="2.0">
        <channel>
          <title>Medici RSS feed</title>
          <description>The latest events happening in Medici</description>
          <link>{routes.Application.index().absoluteURL()}</link>
          {
          for (item <- feedItems) yield {
            <item>
              <title>Event</title>
              <description>{item.user.fullName}{views.html.newsFeedCardEmail(item)}</description>
              <link>{getItemLink(item).absoluteURL()}</link>
            </item>
          }
          }
        </channel>
      </rss>
    )
  }
}
