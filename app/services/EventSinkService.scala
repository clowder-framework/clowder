package services

import models.{Collection, Dataset, ExtractorInfo, File, ProjectSpace, User}

import java.net.URI
import java.time.Instant
import play.api.{Logger, Play}
import play.api.Play.current
import play.api.libs.json.{JsValue, Json}

class EventSinkService {
  val messageService: MessageService = DI.injector.getInstance(classOf[MessageService])
  val userService: UserService = DI.injector.getInstance(classOf[UserService])

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

    Logger.info("Submitting message to event sink exchange: " + Json.stringify(metadata))

    //val message = EventSinkMessage(Instant.now().getEpochSecond, category, metadata)
    messageService.submit(exchangeName, queueName, metadata)
  }

  /** Log an event when user views a dataset */
  def logDatasetViewEvent(dataset: Dataset, viewer: Option[User]) = {
    Logger.info("User viewed a dataset: " + dataset.id.stringify)
    logEvent("view_resource", Json.obj(
      "type" -> "dataset",
      "resource_id" -> dataset.id,
      "resource_name" -> dataset.name,
      "author_id" -> dataset.author.id,
      "author_name" -> dataset.author.fullName,
      "viewer_id" -> viewer.get.id,
      "viewer_name" -> viewer.get.getMiniUser.fullName
    ))
  }

  /** Log an event when user views a file */
  def logFileViewEvent(file: File, viewer: Option[User]) = {
    Logger.info("User viewed a file: " + file.id.stringify)
    logEvent("view_resource", Json.obj(
      "type" -> "file",
      "resource_id" -> file.id,
      "resource_name" -> file.filename,
      "author_id" -> file.author.id,
      "author_name" -> file.author.fullName,
      "viewer_id" -> viewer.get.id,
      "viewer_name" -> viewer.get.getMiniUser.fullName
    ))
  }

  /** Log an event when user views a collection */
  def logCollectionViewEvent(collection: Collection, viewer: Option[User]) = {
    Logger.info("User viewed a collection: " + collection.id.stringify)
    logEvent("view_resource", Json.obj(
      "type" -> "collection",
      "resource_id" -> collection.id,
      "resource_name" -> collection.name,
      "author_id" -> collection.author.id,
      "author_name" -> collection.author.fullName,
      "viewer_id" -> viewer.get.id,
      "viewer_name" -> viewer.get.getMiniUser.fullName
    ))
  }

  /** Log an event when user views a space */
  def logSpaceViewEvent(space: ProjectSpace, viewer: Option[User]) = {
    Logger.info("User viewed a space: " + space.id.stringify)
    (viewer, userService.get(space.creator)) match {
      case (Some(v), Some(author)) => {
        logEvent("view_resource", Json.obj(
          "type" -> "space",
          "resource_id" -> space.id,
          "resource_name" -> space.name,
          "author_id" -> space.creator.stringify,
          "author_name" -> author.fullName,
          "viewer_id" -> v.id,
          "viewer_name" -> v.getMiniUser.fullName
        ))
      }
      case (None, Some(author)) => {
        logEvent("view_resource", Json.obj(
          "type" -> "space",
          "resource_id" -> space.id,
          "resource_name" -> space.name,
          "author_id" -> author.id,
          "author_name" -> author.fullName,
          "viewer_id" -> "",
          "viewer_name" -> "Anonymous"
        ))
      }
      case (Some(v), None) => {
        // TODO: Is this a real case? Is this needed?
        logEvent("view_resource", Json.obj(
          "type" -> "space",
          "resource_id" -> space.id,
          "resource_name" -> space.name,
          "author_id" -> space.creator.stringify,
          "author_name" -> "",
          "viewer_id" -> v.id,
          "viewer_name" -> v.getMiniUser.fullName
        ))
      }
      case (None, None) => {
        // TODO: Is this a real case? Is this needed?
        logEvent("view_resource", Json.obj(
          "type" -> "space",
          "resource_id" -> space.id,
          "resource_name" -> space.name,
          "author_id" -> space.creator.stringify,
          "author_name" -> "",
          "viewer_id" -> "",
          "viewer_name" -> "Anonymous"
        ))
      }
    }
  }

  def logSubmitFileToExtractorEvent(file: File, extractor: ExtractorInfo, submitter: Option[User]) = {
    logEvent("extraction", Json.obj(
      "type" -> "file",
      "extractor_name" -> extractor.name,
      "resource_id" -> file.id,
      "resource_name" -> file.filename,
      "author_id" -> file.author.id,
      "author_name" -> file.author.fullName,
      "submitter_id" -> submitter.get.id,
      "submitter_name" -> submitter.get.getMiniUser.fullName
    ))
  }

  def logSubmitDatasetToExtractorEvent(dataset: Dataset, extractor: ExtractorInfo, submitter: Option[User]) = {
    logEvent("extraction", Json.obj(
      "type" -> "dataset",
      "extractor_name" -> extractor.name,
      "resource_id" -> dataset.id,
      "resource_name" -> dataset.name,
      "author_id" -> dataset.author.id,
      "author_name" -> dataset.author.fullName,
      "submitter_id" -> submitter.get.id,
      "submitter_name" -> submitter.get.getMiniUser.fullName
    ))
  }

  def logSubmitSelectionToExtractorEvent(dataset: Dataset, extractor: ExtractorInfo, submitter: Option[User]) = {
    logEvent("extraction", Json.obj(
      "type" -> "selection",
      "extractor_name" -> extractor.name,
      "resource_id" -> dataset.id,
      "resource_name" -> dataset.name,
      "author_id" -> dataset.author.id,
      "author_name" -> dataset.author.fullName,
      "submitter_id" -> submitter.get.id,
      "submitter_name" -> submitter.get.getMiniUser.fullName
    ))
  }

  def logFileUploadEvent(file: File, dataset:Dataset, uploader: Option[User]) = {
    logEvent("upload", Json.obj(
      "dataset_id" -> dataset.id,
      "dataset_name" -> dataset.name,
      "dataset_author_name" -> dataset.author.fullName,
      "dataset_author_id" -> dataset.author.id,
      "uploader_id" -> uploader.get.id,
      "uploader_name" -> uploader.get.getMiniUser.fullName,
      "filename" -> file.filename,
      "length" -> file.length
    ))
  }

  def logFileDownloadEvent(file: File, /*dataset: Dataset,*/ downloader: Option[User]) = {
    logEvent("download", Json.obj(
      /*"dataset_id" -> dataset.id,
      "dataset_name" -> dataset.name,
      "dataset_author_name" -> dataset.author.fullName,
      "dataset_author_id" -> dataset.author.id,*/
      "type" -> "file",
      "downloader_id" -> downloader.get.id,
      "downloader_name" -> downloader.get.getMiniUser.fullName,
      "filename" -> file.filename,
      "length" -> file.length
    ))
  }

  def logDatasetDownloadEvent(dataset: Dataset, downloader: Option[User]) = {
    logEvent("download", Json.obj(
      "type" -> "dataset",
      "dataset_id" -> dataset.id,
      "dataset_name" -> dataset.name,
      "dataset_author_name" -> dataset.author.fullName,
      "dataset_author_id" -> dataset.author.id,
      "downloader_id" -> downloader.get.id,
      "downloader_name" -> downloader.get.getMiniUser.fullName,
      "files_length" -> dataset.files.length,
      "folder_length" -> dataset.folders.length
    ))
  }
}

//case class EventSinkMessage(created: Long, category: String, metadata: JsValue)