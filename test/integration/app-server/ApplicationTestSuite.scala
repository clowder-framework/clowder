package integration

import org.scalatest.Suites
import org.scalatestplus.play.OneAppPerSuite
import play.api.test.FakeApplication

/**
 * Test basic application
 */
class ApplicationTestSuite  extends Suites (
  new ApplicationSpec
) with OneAppPerSuite
{
  implicit override lazy val app: FakeApplication =
    FakeApplication(additionalConfiguration = Map("ehcacheplugin" -> "disabled", "withoutPlugins" -> List(
      "services.RabbitmqPlugin",
      "services.VersusPlugin")))

}
