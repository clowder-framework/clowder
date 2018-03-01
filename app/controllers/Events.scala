package controllers
import javax.inject.Inject

import services.EventService
import models.{ Event, UserStatus }

/**
 * Events controller
 */
class Events @Inject() (events: EventService) extends SecuredController {

  object ActivityFound extends Exception {}

  /**
   * Get 5 more events Ordered by time.
   */
  def getEvents(index: Int) = UserAction(needActive = false) { implicit request =>
    implicit val user = request.user

    user match {
      case Some(clowderUser) if !(clowderUser.status == UserStatus.Inactive) => {
        val newEventNumber = 10
        var newsfeedEvents = user.fold(List.empty[Event])(u => events.getEvents(u.followedEntities, Some(index * newEventNumber)))
        newsfeedEvents = newsfeedEvents ::: events.getRequestEvents(user, Some(index * newEventNumber))
        newsfeedEvents = (newsfeedEvents ::: events.getEventsByUser(clowderUser, Some(index * newEventNumber)) ::: events.getCommentEvent(clowderUser, Some(index * newEventNumber)))
        if (newsfeedEvents.size > index * newEventNumber - newEventNumber) {
          newsfeedEvents = newsfeedEvents.sorted(Ordering.by((_: Event).created).reverse).distinct.take(index * newEventNumber).takeRight(newEventNumber)

          Ok(views.html.eventsList(newsfeedEvents))
        } else Ok(views.html.eventsList(List.empty[Event]))
      }
      case _ => BadRequest("Unauthorized.")
    }
  }
}
