package services

import models.{Collection, Dataset, ExtractorInfo, File, ProjectSpace, User}

import java.net.URI
import java.time.Instant
import play.api.{Logger, Play}
import play.api.Play.current
import play.api.libs.json.{JsObject, JsValue, Json}

object EventSinkService {
  val EXCHANGE_NAME_CONFIG_KEY = "eventsink.exchangename"
  val QUEUE_NAME_CONFIG_KEY = "eventsink.queuename"

  val EXCHANGE_NAME_DEFAULT_VALUE = "clowder.metrics"
  val QUEUE_NAME_DEFAULT_VALUE = ""
}

class EventSinkService {
  val messageService: MessageService = DI.injector.getInstance(classOf[MessageService])
  val userService: UserService = DI.injector.getInstance(classOf[UserService])
  val appConfig: AppConfigurationService = DI.injector.getInstance(classOf[AppConfigurationService])

  /** Event Sink exchange name in RabbitMQ */
  val exchangeName = Play.configuration.getString(EventSinkService.EXCHANGE_NAME_CONFIG_KEY)
    .getOrElse(EventSinkService.EXCHANGE_NAME_DEFAULT_VALUE)

  /** Event Sink queue name in RabbitMQ */
  val queueName = Play.configuration.getString(EventSinkService.QUEUE_NAME_CONFIG_KEY)
    .getOrElse(EventSinkService.QUEUE_NAME_DEFAULT_VALUE)

  def logEvent(message: JsValue) = {
    // Inject timestamp before logging the event
    val event = message.as[JsObject] + ("created" -> Json.toJson(java.util.Date.from(Instant.now())))
    Logger.info("Submitting message to event sink exchange: " + Json.stringify(event))
    try {
      messageService.submit(exchangeName, queueName, event, "fanout")
    } catch {
      case e: Throwable => { Logger.error("Failed to submit event sink message", e) }
    }
  }

  /** Log an event when user signs up */
  def logUserSignupEvent(user: User) = {
    Logger.debug("New user signed up: " + user.id.stringify)
    logEvent(Json.obj(
      "category" -> "user_activity",
      "type" -> "signup",
      "user_id" -> user.id,
      "user_name" -> user.fullName
    ))
  }

  /** Log an event when user logs in */
  def logUserLoginEvent(user: User) = {
    Logger.debug("User logged in: " + user.id.stringify)
    logEvent(Json.obj(
      "category" -> "user_activity",
      "type" -> "login",
      "user_id" -> user.id,
      "user_name" -> user.fullName
    ))
  }

  /** Log an event when user views a dataset */
  def logDatasetViewEvent(dataset: Dataset, viewer: Option[User]) = {
    Logger.debug("User viewed a dataset: " + dataset.id.stringify)
    logEvent(Json.obj(
      "category" -> "view_resource",
      "type" -> "dataset",
      "resource_id" -> dataset.id,
      "resource_name" -> dataset.name,
      "author_id" -> dataset.author.id,
      "author_name" -> dataset.author.fullName,
      "user_id" -> viewer.getOrElse(User.anonymous).id,
      "user_name" -> viewer.getOrElse(User.anonymous).getMiniUser.fullName
    ))
  }

  /** Log an event when user views a file */
  def logFileViewEvent(file: File, viewer: Option[User]) = {
    Logger.debug("User viewed a file: " + file.id.stringify)
    logEvent(Json.obj(
      "category" -> "view_resource",
      "type" -> "file",
      "resource_id" -> file.id,
      "resource_name" -> file.filename,
      "author_id" -> file.author.id,
      "author_name" -> file.author.fullName,
      "user_id" -> viewer.getOrElse(User.anonymous).id,
      "user_name" -> viewer.getOrElse(User.anonymous).getMiniUser.fullName
    ))
  }

  /** Log an event when user views a collection */
  def logCollectionViewEvent(collection: Collection, viewer: Option[User]) = {
    Logger.debug("User viewed a collection: " + collection.id.stringify)
    logEvent(Json.obj(
      "category" -> "view_resource",
      "type" -> "collection",
      "resource_id" -> collection.id,
      "resource_name" -> collection.name,
      "author_id" -> collection.author.id,
      "author_name" -> collection.author.fullName,
      "user_id" -> viewer.getOrElse(User.anonymous).id,
      "user_name" -> viewer.getOrElse(User.anonymous).getMiniUser.fullName
    ))
  }

  /** Log an event when user views a space */
  def logSpaceViewEvent(space: ProjectSpace, viewer: Option[User]) = {
    Logger.debug("User viewed a space: " + space.id.stringify)
    (viewer, userService.get(space.creator)) match {
      case (Some(v), Some(author)) => {
        logEvent(Json.obj(
          "category" -> "view_resource",
          "type" -> "space",
          "resource_id" -> space.id,
          "resource_name" -> space.name,
          "author_id" -> space.creator.stringify,
          "author_name" -> author.fullName,
          "user_id" -> v.id,
          "user_name" -> v.getMiniUser.fullName
        ))
      }
      case (None, Some(author)) => {
        logEvent(Json.obj(
          "category" -> "view_resource",
          "type" -> "space",
          "resource_id" -> space.id,
          "resource_name" -> space.name,
          "author_id" -> author.id,
          "author_name" -> author.fullName,
          "user_id" -> User.anonymous.id,
          "user_name" -> User.anonymous.fullName
        ))
      }
      case (Some(v), None) => {
        // TODO: Is this a real case? Is this needed?
        logEvent(Json.obj(
          "category" -> "view_resource",
          "type" -> "space",
          "resource_id" -> space.id,
          "resource_name" -> space.name,
          "author_id" -> space.creator.stringify,
          "author_name" -> "",
          "user_id" -> v.id,
          "user_name" -> v.getMiniUser.fullName
        ))
      }
      case (None, None) => {
        // TODO: Is this a real case? Is this needed?
        logEvent(Json.obj(
          "category" -> "view_resource",
          "type" -> "space",
          "resource_id" -> space.id,
          "resource_name" -> space.name,
          "author_id" -> space.creator.stringify,
          "author_name" -> "",
          "user_id" -> User.anonymous.id,
          "user_name" -> User.anonymous.fullName
        ))
      }
    }
  }

  def logSubmitFileToExtractorEvent(file: File, extractorName: String, submitter: Option[User]) = {
    logEvent(Json.obj(
      "category" -> "extraction",
      "type" -> "file",
      "extractor_name" -> extractorName,
      "resource_id" -> file.id,
      "resource_name" -> file.filename,
      "author_id" -> file.author.id,
      "author_name" -> file.author.fullName,
      "user_id" -> submitter.getOrElse(User.anonymous).id,
      "user_name" -> submitter.getOrElse(User.anonymous).getMiniUser.fullName
    ))
  }

  def logSubmitDatasetToExtractorEvent(dataset: Dataset, extractorName: String, submitter: Option[User]) = {
    logEvent(Json.obj(
      "category" -> "extraction",
      "type" -> "dataset",
      "extractor_name" -> extractorName,
      "resource_id" -> dataset.id,
      "resource_name" -> dataset.name,
      "author_id" -> dataset.author.id,
      "author_name" -> dataset.author.fullName,
      "user_id" -> submitter.getOrElse(User.anonymous).id,
      "user_name" -> submitter.getOrElse(User.anonymous).getMiniUser.fullName
    ))
  }

  def logSubmitSelectionToExtractorEvent(dataset: Dataset, extractorName: String, submitter: Option[User]) = {
    // TODO: Is this a real case? Is this needed?
    logEvent(Json.obj(
      "category" -> "extraction",
      "type" -> "selection",
      "extractor_name" -> extractorName,
      "resource_id" -> dataset.id,
      "resource_name" -> dataset.name,
      "author_id" -> dataset.author.id,
      "author_name" -> dataset.author.fullName,
      "user_id" -> submitter.getOrElse(User.anonymous).id,
      "user_name" -> submitter.getOrElse(User.anonymous).getMiniUser.fullName
    ))
  }

  def logFileUploadEvent(file: File, dataset: Option[Dataset], uploader: Option[User]) = {
    dataset match {
      case Some(d) => {
        logEvent(Json.obj(
          "category" -> "upload",
          "dataset_id" -> d.id,
          "dataset_name" -> d.name,
          "author_name" -> d.author.fullName,
          "author_id" -> d.author.id,
          "user_id" -> uploader.getOrElse(User.anonymous).id,
          "user_name" -> uploader.getOrElse(User.anonymous).getMiniUser.fullName,
          "resource_name" -> file.filename,
          "size" -> file.length
        ))
      }
      case None => {
        logEvent(Json.obj(
          "category" -> "upload",
          "user_id" -> uploader.getOrElse(User.anonymous).id,
          "user_name" -> uploader.getOrElse(User.anonymous).getMiniUser.fullName,
          "resource_name" -> file.filename,
          "size" -> file.length
        ))
      }
    }
  }

  def logFileDownloadEvent(file: File, downloader: Option[User]) = {
    logEvent(Json.obj(
      "category" -> "download",
      "type" -> "file",
      "resource_id" -> file.id,
      "resource_name" -> file.filename,
      "author_id" -> file.author.id,
      "author_name" -> file.author.fullName,
      "user_id" -> downloader.getOrElse(User.anonymous).id,
      "user_name" -> downloader.getOrElse(User.anonymous).getMiniUser.fullName,
      "size" -> file.length
    ))
  }

  def logDatasetDownloadEvent(dataset: Dataset, downloader: Option[User]) = {
    logEvent(Json.obj(
      "category" -> "download",
      "type" -> "dataset",
      "resource_id" -> dataset.id,
      "resource_name" -> dataset.name,
      "author_name" -> dataset.author.fullName,
      "author_id" -> dataset.author.id,
      "user_id" -> downloader.getOrElse(User.anonymous).id,
      "user_name" -> downloader.getOrElse(User.anonymous).getMiniUser.fullName,
      "size" -> (dataset.files.length + dataset.folders.length)
    ))
  }
}

//case class EventSinkMessage(created: Long, category: String, metadata: JsValue)
