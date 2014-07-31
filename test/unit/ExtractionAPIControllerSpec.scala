package unit

import org.scalatest.Assertions._
import play.api.test.FakeApplication
import play.api.test.FakeRequest
import play.api.test.Helpers._
import org.mockito.Mockito.when
import org.scalatest.mock.MockitoSugar._
import org.mockito.Mockito.doNothing
import play.api.http.HeaderNames
import services.ExtractorService
import services.ExtractionService
import services.ExtractionRequestsService
import services.PreviewService
import services.RdfSPARQLService
import services.ThumbnailService
import api.Extractions
import services.FileService
import services.DI
import javax.inject.Inject
import scala.concurrent.Future
import play.api.mvc.SimpleResult
import play.api.mvc.PlainResult
import play.api.GlobalSettings
import play.api.Play.current
import play.api.libs.concurrent.Execution.Implicits._
import play.api.libs.json.Json

/*
 * Unit Test for  
 * 
 */

class ExtractionAPIControllerSpec extends UnitSpec{

  val fakeApplicationWithGlobal = FakeApplication(withGlobal = Some(new GlobalSettings() {
    def onStart(app: App) { println("Hello world!") }
  }))

  "ExtractionApiController#getExtractorNames" should
    "return List of name" in running(fakeApplicationWithGlobal) {
      var mockfiles = mock[FileService]
      var mockExtractions = mock[ExtractionService]
      var mockDTS = mock[ExtractionRequestsService]
      var mockExtractors = mock[ExtractorService]
      var mockPreviews = mock[PreviewService]
      var mockRdf = mock[RdfSPARQLService]
      var mockthumbnails = mock[ThumbnailService]

      when(mockExtractors.getExtractorNames).thenReturn(List("ncsa.cv.face", "ncsa.ocr"))
      when(mockExtractors.getExtractorServerIPList).thenReturn(List("dts1.ncsa.illinois.edu","141.142.220.244"))
      when(mockExtractors.getExtractorInputTypes).thenReturn(List("image","text"))

      doNothing().when(mockExtractors).insertExtractorNames(List("ncsa.cv.face", "ncsa.ocr"))
      doNothing().when(mockExtractors).insertServerIPs(List("dts1.ncsa.illinois.edu","141.142.220.244"))
      doNothing().when(mockExtractors).insertInputTypes(List("image","text"))

      val extractions_apicontroller = new api.Extractions(mockfiles, mockExtractions, mockDTS, mockExtractors, mockPreviews, mockRdf, mockthumbnails)

      val request = FakeRequest().withHeaders(ACCEPT -> "application/json")
      info("headers: " + request.headers)

      val result_exnames = extractions_apicontroller.getExtractorNames.apply(FakeRequest())
      val bodyText = contentAsJson(result_exnames)

      val test = Json.toJson(Map("Extractors" -> List("ncsa.cv.face", "ncsa.ocr")))
      info(test.toString)
      bodyText should equal(test)

    }


}