package unit

import java.util.Date
import models.{Tag, UUID, File,LDMetadata, UserAgent}
import securesocial.core.{AuthenticationMethod, IdentityId, SocialUser}
import java.net.URL
import play.api.libs.json.JsObject

/**
 * Mixin of default data to use for testing.
 *
 * @author Luigi Marini
 */
trait TestData {
  var testUser = SocialUser(identityId = IdentityId("john@doe.com","userpass"),
    firstName = "John",
    lastName = "Doe",
    fullName = "John Doe",
    email = Some("john@doe.com"),
    avatarUrl = None,
    authMethod = AuthenticationMethod.UserPassword)
  
  var testFile = File(id = UUID.generate, filename = "foo.txt", author = testUser, uploadDate =  new Date, contentType = "text/plain")

  var testTag = Tag(UUID.generate, "foo", None, None, new Date)
    
  val testCreator = UserAgent(id= UUID.generate, typeOfAgent="cat:user", userId = Some(new URL("http://xyz.com/user")))
  
  val testLDMetadata = LDMetadata(
    id=UUID.generate,
    attachedTo = Map("file_id"->UUID.generate), 
    createdAt = new Date,
    creator = testCreator,
    content = new JsObject(Seq()))
  
}
