package integration

import org.scalatest._
import play.api.test._

/**
 * Application fixture to setup a fake application for functional tests.
 *
 *  @author Luigi Marini
 */
//@DoNotDiscover
trait AppFixture extends SuiteMixin { this: Suite =>

  val excludedPlugins = List(
    "services.RabbitmqPlugin"
  )

  val includedPlugins = List(
//    "services.mongodb.MongoUserService"
  )

  implicit val app: FakeApplication = FakeApplication(withoutPlugins=excludedPlugins,additionalPlugins = includedPlugins)

  abstract override def withFixture(test: NoArgTest) = Helpers.running(app) {
      super.withFixture(test)
  }
}