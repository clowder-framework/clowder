import java.util.Date
import models.{Tag, UUID, File}
import securesocial.core.{AuthenticationMethod, IdentityId, SocialUser}

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
}
