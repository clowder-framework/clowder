package unit.metadata
import models.Metadata
import org.scalatest.mock.MockitoSugar._
import models.UserAgent
import unit.TestData
import unit.UnitSpec

/**
 * Unit Test for LDMetadata model
 * @author Smruti Padhy 
 */

class MetadataSpec extends UnitSpec with metadataTestData {
  val mockLDMetadata = mock[Metadata]
   
  "A metadata" should "have attachedTo set" in {
    assert( testMetadata.attachedTo.contains("file_id") == true)
  }  
  
  "A metadata" should "have a creator" in {
    assert(testMetadata.creator != null)
  }
  "A metadata creator agent" should "have a type and agent specific id" in {
    info("creator "+ testMetadata.creator)
    val ua = testMetadata.creator.asInstanceOf[UserAgent]
    assert(testMetadata.creator.isInstanceOf[UserAgent] == true)
    assert(testMetadata.creator.typeOfAgent != null)
    assert(ua.userId != None)
  }

}