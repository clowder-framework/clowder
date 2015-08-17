package api

import java.net.URL
import java.util.Date
import javax.inject.{Inject, Singleton}

import jsonutils.JsonUtil
import models.{UserAgent, UUID, ResourceRef}
import play.api.Logger
import play.api.libs.json._
import play.api.libs.json.Json._
import services.{ContextLDService, MetadataService}
import play.api.Play.configuration

/**
 * Manipulate generic metadata.
 */
@Singleton
class Metadata @Inject()(metadataService: MetadataService, contextService: ContextLDService) extends ApiController {

  def search() = SecuredAction(authorization = WithPermission(Permission.SearchDatasets)) { request =>
    Logger.debug("Searching metadata")
    val results = metadataService.search(request.body)
    Ok(toJson(results))
  }
}
