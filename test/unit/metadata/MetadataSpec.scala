package unit.metadata
import models._
import unit.TestData
import unit.UnitSpec
import org.scalatest.mock.MockitoSugar._
import org.mockito.Mockito.when
import java.net.URL
/**
 * Unit Test for Metadata model
 * 
 * @author Smruti Padhy 
 */

class MetadataSpec extends UnitSpec {

  val mockMetadata = mock[Metadata]
  val id = UUID.generate
  when(mockMetadata.attachedTo).thenReturn(ResourceRef(ResourceRef.file, UUID.generate))
  when(mockMetadata.creator).thenReturn(new UserAgent(id = UUID.generate, typeOfAgent="cat:user",
    MiniUser(id, "Test User", "", None), userId = Some(new URL("http://dts.ncsa.illinois.edu/user06"))))
  //when(mockMetadata.creator).thenReturn(new UserAgent(id = UUID.generate, userId = Some("http://xyz.com/user")))
      
  "A metadata" should "have attachedTo set" in {
    assert( mockMetadata.attachedTo.resourceType == "file")
  }  
  
  "A metadata" should "have a creator" in {
    assert(mockMetadata.creator != null)
  }
  
  "A metadata creator agent" should "have a type and agent specific id" in {
    info("creator "+ mockMetadata.creator)
    assert(mockMetadata.creator.isInstanceOf[UserAgent] == true)
    assert(mockMetadata.creator.typeOfAgent == "user")
    assert(mockMetadata.creator.asInstanceOf[UserAgent].userId == Some(new URL("http://dts.ncsa.illinois.edu/user06")))
  }

}