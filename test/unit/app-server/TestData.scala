package unit

import java.util.Date
import models.{Tag, UUID, File, Metadata, UserAgent}
import securesocial.core.{AuthenticationMethod, IdentityId, SocialUser}
import java.net.URL
import play.api.libs.json.JsObject
import models.MiniUser

/**
 * Mixin of default data to use for testing.
 *
 */
trait TestData {
  var testUser = MiniUser(
    id = UUID("56d8afec7d840aec3068a334"),
    fullName = "John Doe",
    email = Some("john@doe.com"),
    avatarURL = ""
    )
  
  var testFile = File(id = UUID.generate, filename = "foo.txt", author = testUser, uploadDate =  new Date, contentType = "text/plain")

  var testTag = Tag(UUID.generate, "foo", None, None, new Date)
    
  }
