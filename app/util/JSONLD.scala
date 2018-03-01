package util

import models.{Metadata, ResourceRef}
import play.api.Logger
import play.api.libs.json.Json._
import play.api.libs.json.{JsArray, JsObject, JsString, JsValue}
import services.{ContextLDService, DI}

/**
 * Utility functions for JSON-LD manipulations.
 */
object JSONLD {

  /**
   * Converts models.Metadata object and context information to JsValue object.
   */
  def jsonMetadataWithContext(metadata: Metadata, baseUrlExcludingContext: String = "", isHttps: Boolean = false): JsValue = {
    val contextService: ContextLDService = DI.injector.getInstance(classOf[ContextLDService])
    // check if there is a context url or a local context definition
    val contextLd = metadata.contextId.flatMap(contextService.getContextById(_))
    val contextJson: Option[JsObject] =
    // both context url and context document are defined
      if (contextLd.isDefined && metadata.contextURL.isDefined)
        Some(JsObject(Seq("@context" -> JsArray(Seq(contextLd.get, JsString(metadata.contextURL.get.toString))))))
      // only the local context definition is defined
      else if (contextLd.isDefined && metadata.contextURL.isEmpty)
      // only the external context url is defined
        Some(JsObject(Seq("@context" -> contextLd.get)))
      else if (contextLd.isEmpty && metadata.contextURL.isDefined)
        Some(JsObject(Seq("@context" -> JsString(metadata.contextURL.get.toString))))
      // no context definition available
      else None

    // Find resource type
    val resourceType = metadata.attachedTo.resourceType

    // Add protocol to URL
    val urlWithProtocol = if (!isHttps)
      "http://" + baseUrlExcludingContext
    else
      "https://" + baseUrlExcludingContext

    // Get resource URL
    val resourceUrl = resourceType match {
      case ResourceRef.file =>
        controllers.routes.Files.file(metadata.attachedTo.id)
      case ResourceRef.dataset =>
        controllers.routes.Datasets.dataset(metadata.attachedTo.id)
      case ResourceRef.collection =>
        controllers.routes.Collections.collection(metadata.attachedTo.id)
      case _ =>
        Logger.error("Resource type mismatch. Found "  + resourceType.toString + ".")
        None
    }

    // Generate resource metadata json
    val resourceJson = JsObject(Seq(
        "attached_to" -> JsObject(Seq(
          "resource_type" -> JsString("cat:" + resourceType.toString.tail),
          "url" -> JsString(urlWithProtocol + resourceUrl.toString))
        )
      )
    )

    //convert metadata to json using implicit writes in Metadata model
    val metadataJson = resourceJson ++ toJson(metadata).asInstanceOf[JsObject]

    //combine the two json objects and return
    if (contextJson.isEmpty) metadataJson else contextJson.get ++ metadataJson
  }
}
