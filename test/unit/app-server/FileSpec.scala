package unit

import models.File
import org.scalatest.mock.MockitoSugar._

/**
 * Test file models and controllers.
 *
 * @author Luigi Marini
 */
class FileSpec extends UnitSpec with TestData {

  val mockFile = mock[File]

  "A file" should "have an author" in {
    assert(testFile.author != null)
  }

  "2" should "not be equal to 3" in {
    val left = 2
    val right = 3
    assert(left != right)
  }

  "5 - 2" should "equal to 3" in {
    val a = 5
    val b = 2
    assertResult(3) {
      a - b
    }
  }
}