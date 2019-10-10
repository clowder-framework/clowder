package models

import play.api.libs.json.{Writes, Json}
import play.api.libs.json._

/**
 * A QueuedAction is a pending action to be performed on some resource asynchronously.
  *
  * Currently, different implementations have separate parameters, for example
  *   elastic_parameters = for queued actions relating to Elasticsearch indexing
  *   rabbit_parameters = for queued actions relating to RabbitMQ submission (not yet implemented)
 */
// ElasticsearchQueue service action parameters
case class ElasticsearchParameters(
                                    recursive: Boolean = false
                                  ) {}

object ElasticsearchParameters {
  implicit val elasticParamsWrites = new Writes[ElasticsearchParameters] {
    def writes(params: ElasticsearchParameters): JsValue = Json.obj(
      "recursive" -> params.recursive.toString
    )
  }
}

case class QueuedAction(
  id: UUID = UUID.generate,
  action: String = "",
  target: Option[ResourceRef] = None,
  elastic_parameters: Option[ElasticsearchParameters] = None
) {}

object QueuedAction {
  implicit val queuedActionWrites = new Writes[QueuedAction] {
    def writes(action: QueuedAction): JsValue = {
      Json.obj(
        "id" -> action.id.toString,
        "action" -> action.action,
        "target" -> action.target,
        "elastic_parameters" -> action.elastic_parameters
      )
    }
  }
}

