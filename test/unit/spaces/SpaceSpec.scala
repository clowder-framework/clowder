package unit.spaces

import models.ProjectSpace
import org.scalatest.mock.MockitoSugar._
import unit.UnitSpec
import org.mockito.Mockito.when

/**
 * Placeholder for spaces unit test.
 *
 * @author Luigi Marini
 *
 */
class SpaceSpec extends UnitSpec {

  val mockSpace = mock[ProjectSpace]

  when(mockSpace.name).thenReturn("N/A")

  "A space" should "have a name" in {
    assert(mockSpace.name != null)
  }

}
