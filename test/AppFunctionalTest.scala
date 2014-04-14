import play.api.test._
import play.api.test.Helpers._

/**
 * Application functional test. Load the full application.
 *
 * @author Luigi Marini
 */
class ApplicationFunctionalTest extends IntegrationSpec {

  "Application" should "load home page" in {
    running(TestServer(3333), HTMLUNIT) { browser =>
      browser.goTo("http://localhost:3333/")
      browser.pageSource should include("Medici 2.0")
    }
  }

  "Application" should "load files page" in {
    running(TestServer(3333), HTMLUNIT) { browser =>
      browser.goTo("http://localhost:3333/files")
      browser.pageSource should include("Files")
    }
  }
}

/**
 * This throws an java.lang.IllegalStateException: cannot enqueue after timer shutdown due to the Akka timer
 * in securesocial.core.UserServicePlugin.onStart(UserService.scala:129). Running a test server for each test
 * avoids this (see above).
 */
class ApplicationFunctionalTest2 extends IntegrationSpec with ServerFixture {

  "Application" should "load home page" in {
    go to "http://localhost:3333/"
    pageSource should include("Medici 2.0")
  }

  "Application" should "load files page" in {
    go to "http://localhost:3333/files"
    pageSource should include("Files")
  }
}
