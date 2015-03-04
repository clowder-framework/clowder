package unit.contextld

import unit.UnitSpec
import org.scalatest.mock.MockitoSugar._
import org.mockito.Mockito.when
import models.UUID
import java.net.URL
import models.ContextLD
import play.api.libs.json.JsObject
import play.api.libs.json.JsArray
import play.api.libs.json.Json
import play.api.libs.json.JsString

/**
 * Unit Test for ContextLD model
 * 
 * @author Smruti Padhy 
 */

class ContextLDSpec extends UnitSpec{
  
  val mockContext = mock[ContextLD]
  
  when(mockContext.contextName).thenReturn(JsString("ncsa.cv.face"))
  when(mockContext.context).thenReturn(Json.toJson(Map("xyz"->"abc")))  
     
  "A context" should "have context name" in {
    assert(mockContext.contextName == JsString("ncsa.cv.face"))
  }  
  
  "A context" should "have a content" in {
    info("context list"+ mockContext.context)
    assert(mockContext.context != null)
  }
}