package util

import models.Metadata
import play.api.libs.json.Json._
import play.api.libs.json.{JsString, JsArray, JsObject, JsValue}
import services.{ContextLDService, DI}

/**
 * Utility functions for JSON-LD manipulations.
 */
object JSONLD {

  /**
   * Converts models.Metadata object and context information to JsValue object.
   */
  def jsonMetadataWithContext(metadata: Metadata): JsValue = {
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
      // no context defintions available
      else None

    //convert metadata to json using implicit writes in Metadata model
    val metadataJson = (toJson(metadata)).asInstanceOf[JsObject]

    //combine the two json objects and return
    if (contextJson.isEmpty) metadataJson else contextJson.get ++ metadataJson
  }
}
