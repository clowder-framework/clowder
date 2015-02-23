package unit.metadata
import models.Metadata
import models.UserAgent
import unit.TestData
import unit.UnitSpec
import org.scalatest.mock.MockitoSugar._
import org.mockito.Mockito.when
import models.UUID
import java.net.URL
/**
 * Unit Test for Metadata model
 * 
 * @author Smruti Padhy 
 */

class MetadataSpec extends UnitSpec with metadataTestData {
  val mockMetadata = mock[Metadata]
  
  when(mockMetadata.attachedTo.contains("file_id")).thenReturn(true)
  when(mockMetadata.creator).thenReturn(new UserAgent(id= UUID.generate, typeOfAgent="cat:user", userId = Some(new URL("http://xyz.com/user"))))
  when(mockMetadata.creator.typeOfAgent).thenReturn("user")
  when(mockMetadata.creator.asInstanceOf[UserAgent].userId).thenReturn(Some(new URL("http://xyz.com/user"))) 
    
  "A metadata" should "have attachedTo set" in {
    assert( mockMetadata.attachedTo.contains("file_id") == true)
  }  
  
  "A metadata" should "have a creator" in {
    assert(mockMetadata.creator != null)
  }
  "A metadata creator agent" should "have a type and agent specific id" in {
    //info("creator "+ testMetadata.creator)
    //val ua = testMetadata.creator.asInstanceOf[UserAgent]
    //assert(testMetadata.creator.isInstanceOf[UserAgent] == true)
    assert(mockMetadata.creator.typeOfAgent == "user")
    assert(mockMetadata.creator.asInstanceOf[UserAgent].userId == Some(new URL("http://xyz.com/user")))
  }

}