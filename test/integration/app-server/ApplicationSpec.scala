package integration

import play.api.test._
import play.api.test.Helpers._
import org.scalatest._

/**
 * Functional test. This throws an java.lang.IllegalStateException: cannot enqueue after timer shutdown due to the Akka timer
 * in securesocial.core.UserServicePlugin.onStart(UserService.scala:129).
 *
 * Running a test server for each test avoids this problem. See ApplciationFunctionalTest.
 *
 * @author Luigi Marini
 */
@DoNotDiscover
class ApplicationSpec extends IntegrationSpec with ServerFixture {

  implicit val user: Option[securesocial.core.Identity] = None

  "Application" should "send 404 on a bad request" in {
    route(FakeRequest(GET, "/wut")) shouldBe (None)
  }

  "Application" should "render the index page" in {
    val home = route(FakeRequest(GET, "/")).get
    status(home) should equal (OK)
    contentType(home) shouldBe Some("text/html")
    contentAsString(home) should include ("a scalable data repository where you can share, organize and analyze data.")
  }

  "Application" should "render index template" in {
    val html = views.html.index(List.empty, 0, 0, 0, "Medici 2.0", "ABCDEFGHIJKLMNOPQRSTUVWXYZ")
    contentType(html) shouldBe ("text/html")
    contentAsString(html) should include ("Medici 2.0")
    contentAsString(html) should include ("Hello stranger!")
    contentAsString(html) should include ("ABCDEFGHIJKLMNOPQRSTUVWXYZ")
  }

  "Application" should "load home route" in {
    val home = route(FakeRequest(GET, "/")).get
    status(home) should be (OK)
    contentType(home) shouldBe ("text/html")
    contentAsString(home) should include ("Medici")
  }

}
