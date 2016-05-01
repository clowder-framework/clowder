package controllers
import javax.inject.Inject

import services.EventService
import models.Event


/**
  * Events controller
  */
class Events @Inject()(events: EventService) extends SecuredController {

  object ActivityFound extends Exception {}

  /**
    * Get 5 more events Ordered by time.
    */
  def getEvents(index: Int) = UserAction(needActive = false) { implicit request =>
    implicit val user = request.user

    user match {
      case Some(clowderUser) if clowderUser.active => {
        var newsfeedEvents = user.fold(List.empty[Event])(u => events.getEvents(u.followedEntities, Some(index*5)))
        newsfeedEvents = newsfeedEvents ::: events.getRequestEvents(user, Some(index*5))
        newsfeedEvents = (newsfeedEvents ::: events.getEventsByUser(clowderUser, Some(index*5)))
          .sorted(Ordering.by((_: Event).created).reverse).distinct.take(index*5).takeRight(5)

        Ok(views.html.eventsList(newsfeedEvents))
      }
      case _ => BadRequest("Unauthorized.")
    }
  }
}
