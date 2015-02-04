package unit.spaces

import models.{ProjectSpace}
import org.scalatest.mock.MockitoSugar._
import unit.UnitSpec

/**
 * Placeholder for spaces unit test.
 *
 * @author Luigi Marini
 *
 */
class SpaceSpec extends UnitSpec {

  val mockSpace = mock[ProjectSpace]

  val privateSpace =

  "A space" should "have a name" in {
    assert(mockSpace.name != null)
  }

}
