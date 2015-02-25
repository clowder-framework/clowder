package integration.metadata

import java.net.URL
import java.util.Date
import com.google.inject.Guice
import models.{UUID, Metadata, UserAgent}
import org.scalatestplus.play.{OneServerPerSuite, PlaySpec}
import services.{DI, MetadataService, FileService}
import play.api.libs.json.JsObject

/**
 * Test Metadata MongoDB service.
 *
 * @author Smruti Padhy
 */
class MetadataMongoDBSpec extends PlaySpec with OneServerPerSuite{
  //val testCreator = UserAgent(id= UUID.generate, typeOfAgent="cat:user", userId = Some(new URL("http://dts.ncsa.illinois.edu/user06")))
  val testCreator = UserAgent(id= UUID.generate, typeOfAgent="cat:user", userId = Some("http://dts.ncsa.illinois.edu/user06"))
  val fileId = UUID.generate
  val testMetadata = Metadata(
    id = UUID.generate,
    attachedTo = Map("file_id"->fileId), 
    createdAt = new Date,
    creator = testCreator,
    content = new JsObject(Seq()))
    
  "The Metadata MongoDB Service" must {
    "be able to add, retrieve and remove Metadata" in {
      info("insert metadata")
      val injector = Guice.createInjector(new services.ConfigurationModule)
      val metadata : MetadataService =  injector.getInstance(classOf[MetadataService])
      val id = metadata.addMetadata(testMetadata)
      info("new metadata added " + id)
      val retrievedMetadata = metadata.getMetadataById(id)
      info("retrieving metadata " + retrievedMetadata)
      metadata.removeMetadata(id)
      info("remove metadata " + id)
    }
   }


}