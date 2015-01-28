package integration.spaces

import java.util.Date
import com.google.inject.Guice
import models.{UUID, ProjectSpace}
import org.scalatest.GivenWhenThen
import org.scalatestplus.play.{OneServerPerTest, OneServerPerSuite, PlaySpec}
import play.api.GlobalSettings
import play.api.test.{FakeApplication}
import services.{DI, SpaceService}

/**
 * Test Space MongoDB service.
 *
 * @author Luigi Marini
 */
class SpaceMongoDBSpec extends PlaySpec with OneServerPerTest {

  val testSpace = ProjectSpace(
    name  = "N/A",
    description = "N/A",
    created = new Date,
    creator = (UUID.generate, "Fake user"), // attribution:UUID ?
    homePage = List.empty,
    logoURL = None,
    bannerURL = None,
    usersByRole = Map.empty, // roleId -> userId
    collectionCount = 0,
    datasetCount = 0,
    userCount = 0,
    metadata = List.empty
  )

  "The Space MongoDB Service" must {
    "be able to insert new Space" in {
      info("create space")
      val injector = Guice.createInjector(new services.ConfigurationModule)
      val spaces: SpaceService =  injector.getInstance(classOf[SpaceService])
      val outcome = spaces.insert(testSpace)
      info("new space created " + outcome.getOrElse("no space id"))
    }
    "delete existing space" in {
      info("delete space")
    }
  }


}
