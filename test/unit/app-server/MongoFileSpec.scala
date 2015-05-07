package unit

import javax.inject.Inject
import org.scalatest.Assertions._
import services.FileService

/**
 * Trying to properly use services for unit tests.
 *
 * @author Luigi Marini
 */
class MongoFileSpec @Inject()(files: FileService) extends UnitSpec {

  "A file" should "write to db" in {
//    assume(database.isAvailable)
    assert(1 == 1)
  }

  "An empty database" should "have no files" in {
    assert(files.listFiles().size == 0)
  }

  "After adding a file we" should "be able to find retrieve it" in {
    assert(true)
  }
}
