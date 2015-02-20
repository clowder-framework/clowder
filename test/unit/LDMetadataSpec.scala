package unit
import models.{UUID, LDMetadata}
import org.scalatest.mock.MockitoSugar._
import models.UserAgent

/**
 * Unit Test for LDMetadata model
 * @author Smruti Padhy 
 */

class LDMetadataSpec extends UnitSpec with TestData {
  val mockLDMetadata = mock[LDMetadata]
   
  "A metadata" should "have attachedTo set" in {
    assert( testLDMetadata.attachedTo.contains("file_id") == true)
  }  
  
  "A metadata" should "have a creator" in {
    assert(testLDMetadata.creator != null)
  }
  "A metadata creator agent" should "have a type and agent specific id" in {
    info("creator "+ testLDMetadata.creator)
    val ua = testLDMetadata.creator.asInstanceOf[UserAgent]
    assert(testLDMetadata.creator.isInstanceOf[UserAgent] == true)
    assert(testLDMetadata.creator.typeOfAgent != null)
    assert(ua.userId != None)
  }

}