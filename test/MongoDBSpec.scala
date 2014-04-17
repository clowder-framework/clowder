/**
 * Base class for testing MongoDB services. Doesn't do much yet.
 *
 * @author Luigi Marini
 */
class MongoDBSpec extends UnitSpec {

  case class Database() {
    def isAvalable() = true
  }

  val database = Database()
}
