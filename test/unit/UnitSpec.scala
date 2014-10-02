package unit

import org.scalatest._

/**
 * Base class for unit tests.
 *
 * @author Luigi Marini
 */
abstract class UnitSpec extends FlatSpec with Matchers with OptionValues with Inside with Inspectors
