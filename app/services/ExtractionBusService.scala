package services

import java.io.IOException
import java.net.URI
import java.text.SimpleDateFormat
import java.net.URLEncoder

import akka.actor.{Actor, ActorRef, PoisonPill, Props}

import scala.concurrent.duration._
import scala.language.postfixOps
import com.ning.http.client.Realm.AuthScheme
import com.rabbitmq.client.AMQP.BasicProperties
import com.rabbitmq.client.AMQP.Queue
import com.rabbitmq.client._
import javax.inject.Inject
import models._
import org.bson.types.ObjectId
import play.api.http.MimeTypes
import play.api.Play.current
import play.api.libs.json._
import play.api.libs.ws.{Response, WS}
import play.api.{Application, Configuration, Logger, Plugin}
import play.libs.Akka
import securesocial.core.IdentityId
import services.Entity

import scala.concurrent.{Await, Future}
import scala.util.Try

/**
 * Despite the `fileId` be named as such, it is currently serialized as `id` and used as the id of any resource in
 * question. So this will be the preview id in the case of messages operating on a preview, it will be the dataset
 * id in the case of dataset messages (yes there is also `datasetId`, pyclowder2 checks that the two are the same.
 * FIXME make optional fields Option[UUID]
 * FIXME rename fileId to id and add a resourceType field (dataset, file, preview, etc.)
 * TODO drop `intermediateId` or figure out how it works and use accordingly
 * @param fileId this should only be used as
 * @param intermediateId
 * @param host
 * @param queue
 * @param metadata
 * @param fileSize
 * @param datasetId
 * @param flags
 * @param secretKey
 */
case class ExtractorMessage(
                             msgid: UUID,
                             fileId: UUID,
                             notifies: List[String],
                             intermediateId: UUID,
                             host: String,
                             queue: String,
                             metadata: Map[String, Any],
                             fileSize: String,
                             datasetId: UUID = null,
                             flags: String = "",
                             secretKey: String,
                             // new fields
                             routing_key: String,
                             source: Entity,
                             activity: String,
                             target: Option[Entity]
                           )

// TODO add other optional fields
case class Entity(
                   id: ResourceRef,
                   mimeType: Option[String],
                   extra: JsObject
                 )

case class CancellationMessage(id: UUID, queueName: String, msgid: UUID)

object Entity {
  implicit val implicitEntityWrites = Json.format[Entity]
}

trait ExtractionBusService {

  def onStart()

  def onStop()

  def close()

  def connect(): Boolean

  def fileCreated(file: File, dataset: Option[Dataset], host: String, requestAPIKey: Option[String])

  def fileCreated(file: TempFile, host: String, requestAPIKey: Option[String])

  def fileAddedToDataset(file: File, dataset: Dataset, host: String, requestAPIKey: Option[String])

  def fileSetAddedToDataset(dataset: Dataset, filelist: List[File], host: String, requestAPIKey: Option[String])

  def fileRemovedFromDataset(file: File, dataset: Dataset, host: String, requestAPIKey: Option[String])

  def submitFileManually(originalId: UUID, file: File, host: String, queue: String, extraInfo: Map[String, Any],
                         datasetId: UUID, newFlags: String, requestAPIKey: Option[String], user: Option[User]) : Boolean

  def submitDatasetManually(host: String, queue: String, extraInfo: Map[String, Any], datasetId: UUID, newFlags: String,
                            requestAPIKey: Option[String], user: Option[User]) : Boolean

  def metadataAddedToResource(metadataId: UUID, resourceRef: ResourceRef, extraInfo: Map[String, Any], host: String,
                              requestAPIKey: Option[String], user: Option[User])

  def metadataRemovedFromResource(metadataId: UUID, resourceRef: ResourceRef, host: String, requestAPIKey: Option[String], user: Option[User])

  def multimediaQuery(tempFileId: UUID, contentType: String, length: String, host: String, requestAPIKey: Option[String])

  def submitSectionPreviewManually(preview: Preview, sectionId: UUID, host: String, requestAPIKey: Option[String])

  def getExchanges : Future[Response]

  def getQueuesNamesForAnExchange(exchange: String): Future[Response]

  def getBindings: Future[Response]

  def getChannelsList: Future[Response]

  def getQueueDetails(qname: String): Future[Response]

  def getQueueBindings(qname: String): Future[Response]

  def cancelPendingSubmission(id: UUID, queueName: String, msg_id: UUID)

  def resubmitPendingRequests(cancellationQueueConsumer: QueueingConsumer, channel: Channel, cancellationSearchTimeout: Long)

}

