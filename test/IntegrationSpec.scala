import org.scalatest._
import selenium.HtmlUnit

/**
 * Base class for integration/functional tests.
 *
 * @author Luigi Marini
 */
abstract class IntegrationSpec extends FlatSpec with Matchers with OptionValues with HtmlUnit
