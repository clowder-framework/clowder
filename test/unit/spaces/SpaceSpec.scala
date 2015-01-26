package unit.spaces

import models.{ProjectSpace, Space}
import org.scalatest.mock.MockitoSugar._
import unit.UnitSpec

/**
 * Created by lmarini on 1/23/15.
 */
class SpaceSpec extends UnitSpec {

  val mockSpace = mock[ProjectSpace]

  val privateSpace =

  "A space" should "have a name" in {
    assert(mockSpace.name != null)
  }

}
