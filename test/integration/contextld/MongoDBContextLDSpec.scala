package integration.contextld

import java.net.URL
import java.util.Date
import com.google.inject.Guice
import models.{UUID, Metadata, UserAgent}
import org.scalatestplus.play.{OneServerPerSuite, PlaySpec}
import services.{DI, MetadataService, FileService}
import play.api.libs.json.JsObject
import play.api.libs.json.Json
import models.ContextLD
import services.ContextLDService
import play.api.libs.json.JsString
import play.api.libs.json.Writes
import play.api.libs.json.JsValue
import com.mongodb.DBObject
import play.api.libs.json.JsArray

/**
 * Test MongoDB ContextLD Service.
 *
 * @author Smruti Padhy
 */

class MongoDBContextLDSpec extends PlaySpec with OneServerPerSuite {

  val contextExtractor = Json.obj("extractor_id" -> Json.obj("@id" -> "http://dts.ncsa.illinois.edu/api/extractor/id",
    "@type" -> "@id"),
    "score" -> "http://www.vision.caltech.edu/Image_Datasets/Caltech101/score",
    "category" -> "http://www.vision.caltech.edu/Image_Datasets/Caltech101/category")
  val context = Json.arr("http://medici.ncsa.illinois.edu/metadata.jsonld", contextExtractor)
  val testContextld = ContextLD(
    id = UUID.generate,
    contextName = "ncsa.cv.caltech101",
    context = context)

  "The ContextLD MongoDB Service" must {
    "be able to add, retrieve and remove Context" in {
      info("inserting context")
      val injector = Guice.createInjector(new services.ConfigurationModule)
      val contextld: ContextLDService = injector.getInstance(classOf[ContextLDService])
      val id = contextld.addContext(testContextld.contextName, testContextld.context)
      info("new context added " + id)
      val retrievedContext = contextld.getContextById(id)
      retrievedContext match {
        case Some(rc) =>
          info("score" + rc.\\("score")); info("@type=" + rc.\\("@type"))
        case None => None
      }
      info("retrieving context " + retrievedContext)

      val contextbyName = contextld.getContextByName("ncsa.cv.caltech101")
      info("Get the context by Name"+contextbyName )

      contextld.removeContext(id)
      info("remove context" + id)
    }
  }

}