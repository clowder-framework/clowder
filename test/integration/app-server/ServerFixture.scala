package integration

import org.scalatest._
import play.api.test._

/**
 * Server fixture setting up a test server for functional tests.
 *
 * @author Luigi Marini
 */
//@DoNotDiscover
trait ServerFixture extends SuiteMixin { this: Suite =>

  implicit val server: TestServer = TestServer(3333)

  abstract override def withFixture(test: NoArgTest) = Helpers.running(server) {
    super.withFixture(test)
  }
}
