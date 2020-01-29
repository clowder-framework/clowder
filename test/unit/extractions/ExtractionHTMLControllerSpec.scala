package integration

import org.scalatest.Assertions._
import play.api.test.FakeApplication
import play.api.test.FakeRequest
import play.api.test.Helpers._
import org.mockito.Mockito.when
import org.scalatest.mock.MockitoSugar._
import org.mockito.Mockito.doNothing
import services.ExtractorService
import services.ExtractionService
import services.ExtractionBusService
import services.ExtractionRequestsService
import services.PreviewService
import services.ThumbnailService
import services.FileService
import services.DatasetService
import services.AppConfigurationService
import play.api.GlobalSettings
import play.api.libs.concurrent.Execution.Implicits._
import play.api.libs.json.Json
import play.api.Logger
import unit.UnitSpec
import org.scalatestplus.play.OneAppPerSuite
import org.scalatest.DoNotDiscover
import org.scalatestplus.play.PlaySpec
import play.api.Play

/*
 * Integration/Unit Test for Extractions Api Controller 
 * @author Smruti Padhy 
 * 
 */

class ExtractionAPIControllerSpec extends PlaySpec with OneAppPerSuite {
  
  val excludedPlugins = List(
    "services.mongodb.MongoSalatPlugin",
    "com.typesafe.plugin.CommonsMailerPlugin",
    "services.mongodb.MongoDBAuthenticatorStore",
    "securesocial.core.DefaultIdGenerator",
    "securesocial.core.providers.utils.DefaultPasswordValidator",
    "services.SecureSocialTemplatesPlugin",
    "services.mongodb.MongoUserService",
    "securesocial.core.providers.utils.BCryptPasswordHasher",
    "securesocial.core.providers.UsernamePasswordProvider",
    "services.RabbitmqPlugin",
    "services.VersusPlugin")

  implicit override lazy val app: FakeApplication = FakeApplication(withoutPlugins = excludedPlugins, withGlobal = Some(new GlobalSettings() {
		def onStart(app: App) { println("Fake Application Started") }
	}))

  val mockfiles = mock[FileService]
  val mockdatasets = mock[DatasetService]
  val mockExtractions = mock[ExtractionService]
  val mockDTS = mock[ExtractionRequestsService]
  val mockExtractors = mock[ExtractorService]
  val mockPreviews = mock[PreviewService]
  val mockthumbnails = mock[ThumbnailService]
  val mockAppConfig = mock[AppConfigurationService]
  val mockExtractionBusService = mock[ExtractionBusService]

  when(mockExtractors.getExtractorNames(List.empty)).thenReturn(List("ncsa.cv.face", "ncsa.ocr"))
  when(mockExtractors.getExtractorInputTypes).thenReturn(List("image", "text"))
}