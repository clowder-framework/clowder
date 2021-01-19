package services

import models.{Collection, Dataset, File, User}

import java.net.URI
import java.time.Instant
import play.api.{Logger, Play}
import play.api.Play.current
import play.api.libs.json.{JsValue, Json}

// TODO: Wire up to real MessageService
class MockMessageService {
  def submit(exchange: String, routing_key: String, metadata: JsValue) = {
    Logger.info("Message submitted to event sink exchange: " + Json.stringify(metadata))
  }
}

class EventSinkService {
  val messageService: MockMessageService = new MockMessageService() // DI.injector.getInstance(classOf[MessageService])

  /** Event Sink exchange name in RabbitMQ */
  lazy val exchangeName = {
    Play.configuration.getString("eventsink.exchangename").getOrElse("clowder.metrics")
  }

  /** Event Sink queue name in RabbitMQ */
  lazy val queueName = {
    Play.configuration.getString("eventsink.queuename").getOrElse("event.sink")
  }

  def logEvent(category: String, metadata: JsValue) = {
    Logger.info("eventsink.exchangename=" + exchangeName)
    Logger.info("eventsink.queueName=" + queueName)

    //val message = EventSinkMessage(Instant.now().getEpochSecond, category, metadata)
    messageService.submit(exchangeName, queueName, metadata)

    Logger.info("Message submitted to event sink exchange: " + Json.stringify(metadata))
  }

  def logFileUploadEvent(file: Any) = {
    Logger.warn("Hey there")
  }

  /** Log an event when user views a dataset */
  def logDatasetViewEvent(dataset: Dataset, user: Option[User]) = {
    Logger.info("User viewed a dataset: " + dataset.id.stringify)
    logEvent("view_resource", Json.obj(
      "type" -> "dataset",
      "resource_id" -> dataset.id,
      "resource_name" -> dataset.name,
      "author_id" -> dataset.author.id,
      "author_name" -> dataset.author.fullName,
      "viewer_id" -> user.get.id,
      "viewer_name" -> user.get.getMiniUser.fullName
    ))
  }

  /** Log an event when user views a file */
  def logFileViewEvent(file: File, user: Option[User]) = {
    Logger.info("User viewed a file: " + file.id.stringify)
    logEvent("view_resource", Json.obj(
      "type" -> "file",
      "resource_id" -> file.id,
      "resource_name" -> file.filename,
      "author_id" -> file.author.id,
      "author_name" -> file.author.fullName,
      "viewer_id" -> user.get.id,
      "viewer_name" -> user.get.getMiniUser.fullName
    ))
  }

  /** Log an event when user views a collection */
  def logCollectionViewEvent(collection: Collection, user: Option[User]) = {
    Logger.info("User viewed a file: " + collection.id.stringify)
    logEvent("view_resource", Json.obj(
      "type" -> "collection",
      "resource_id" -> collection.id,
      "resource_name" -> collection.name,
      "author_id" -> collection.author.id,
      "author_name" -> collection.author.fullName,
      "viewer_id" -> user.get.id,
      "viewer_name" -> user.get.getMiniUser.fullName
    ))
  }
}

//case class EventSinkMessage(created: Long, category: String, metadata: JsValue)