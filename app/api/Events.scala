package api

import services.mongodb.MongoDBEventService
import javax.inject.Inject
import play.api.Play.current
import play.api.Play.configuration
import models.Event
import play.api.libs.json.Json.toJson


class Events @Inject() (events: MongoDBEventService) extends ApiController {

  /*
   * Add a new event to the database
   */
  def addEvent(event: Event) = SecuredAction(authorization = WithPermission(Permission.AddEvent)) {
    implicit request =>
      events.addEvent(event)
      Ok(toJson("added new event"))
  }

}
