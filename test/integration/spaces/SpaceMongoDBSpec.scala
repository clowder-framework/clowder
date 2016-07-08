package integration.spaces

import java.net.URL
import java.util.Date
import com.google.inject.Guice
import models.{UUID, ProjectSpace}
import org.scalatestplus.play.{OneServerPerSuite, PlaySpec}
import services.{DI, SpaceService}

/**
 * Test Space MongoDB service.
 *
 * @author Luigi Marini
 */
class SpaceMongoDBSpec extends PlaySpec with OneServerPerSuite {

  val testSpace = ProjectSpace(
    name  = "N/A",
    description = "N/A",
    created = new Date,
    creator = UUID.generate, // attribution:UUID ?
    homePage = List(new URL("http://isda.ncsa.illinois.edu/")),
    logoURL = None,
    bannerURL = None,
    collectionCount = 0,
    datasetCount = 0,
    userCount = 0,
    metadata = List.empty
  )

  var id = ""

  "The Space MongoDB Service" must {
    "be able to insert new Space" in {
      info("create space")
      val injector = Guice.createInjector(new services.ConfigurationModule)
      val spaces: SpaceService =  injector.getInstance(classOf[SpaceService])
      val outcome = spaces.insert(testSpace)
      id = outcome.getOrElse("no space id")
      info("new space created " + id)
    }
    "retrieve existing space" in {
      val injector = Guice.createInjector(new services.ConfigurationModule)
      val spaces: SpaceService =  injector.getInstance(classOf[SpaceService])
      val retrievedSpace = spaces.get(UUID(id))
      info("retrieving space " + retrievedSpace.get.homePage)
    }
    "delete existing space" in {
      val injector = Guice.createInjector(new services.ConfigurationModule)
      val spaces: SpaceService =  injector.getInstance(classOf[SpaceService])
      val deletedSpace = spaces.delete(UUID(id))
      info("delete space " + id)
    }
  }


}
