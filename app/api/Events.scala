package api

import play.api.Logger
import play.api.Play.current
import models.{UUID, Collection, Event}
import services._
import play.api.libs.json.{JsObject, JsValue}
import play.api.libs.json.Json.toJson
import javax.inject.{ Singleton, Inject }
import scala.util.{Try, Success, Failure}
import com.wordnik.swagger.annotations.Api
import com.wordnik.swagger.annotations.ApiOperation
import java.util.Date
import controllers.Utils


class Events @Inject() (events: EventService) extends ApiController {

  /*
   * Add a new event to the database
   */
     @ApiOperation(value = "Insert Event",
      notes = "Insert an Event into the Events Collection",
      responseClass = "None", httpMethod = "POST")
  def addEvent(event: Event) = SecuredAction(authorization = WithPermission(Permission.AddEvent)) {
    implicit request =>
      events.addEvent(event)
      Ok(toJson("added new event"))
  }

}
