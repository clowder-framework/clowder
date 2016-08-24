package integration

import models.User
import org.junit.Before
import org.specs2.execute.AsResult
import org.specs2.execute.Result
import play.api.Logger

import play.api.test.{FakeRequest, WithApplication, Writeables, RouteInvokers}
import play.api.mvc.Cookie
import play.api.test.Helpers
import play.api.test.Helpers.cookies
import play.api.test.Helpers.defaultAwaitTimeout


object LoginUtil extends RouteInvokers with Writeables {

//  @Before
//  def setUp() throws Exception {
//
//    val user = new User()
//    user.firstName = "jack"
//    user.lastName = "sparrow"
//    user.email = "test@example.com"
////    user.providerId = "userpasswordid"
////    user.password = "MyTestPassword"
//    user.authMethod = "userPassword"
//    user.save()
//  }


  val loginRequest = FakeRequest(Helpers.POST, "/authenticate/userpass")
    .withFormUrlEncodedBody(("username", "y@ik"), ("password", "4426asrr"))
  var _cookie: Cookie = _

  def cookie = _cookie

  def login() {
    val credentials = cookies(route(loginRequest).get)
    val idCookie = credentials.get("id")
    Logger.debug("guyefyuwy    "+idCookie)
    _cookie = idCookie.get
  }
}

abstract class WithAppLogin extends WithApplication {
  override def around[T: AsResult](t: => T): Result = super.around {
    LoginUtil.login()
    t
  }
}