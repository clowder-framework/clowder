
package integration

import org.scalatest._
import org.scalatestplus.play.OneAppPerSuite
import play.api.test.FakeApplication




class APITestSuite extends Suites (
		new ExtractionsAPIAppSpec ,
		new FilesAPIAppSpec ,
		new DatasetsAPIAppSpec ,
		new CollectionsAPIAppSpec ,
		new PreviewsAPIAppSpec,
		new ApplicationSpec
	) with OneAppPerSuite
{
// Override app if you need a FakeApplication with other than non-default parameters.
   implicit override lazy val app: FakeApplication =
     FakeApplication(additionalConfiguration = Map("ehcacheplugin" -> "disabled", "withoutPlugins" -> List(
    "services.RabbitmqPlugin",
    "services.VersusPlugin")))

}


