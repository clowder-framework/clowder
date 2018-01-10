package controllers

import javax.net.ssl.SSLSocketFactory
import play.api.Logger
import play.api.mvc.{Action}
import play.api.Play.current
import securesocial.core.{SecureSocial, UserService}
import services.LdapProvider
import com.unboundid.ldap.sdk._
import com.unboundid.util.ssl.{JVMDefaultTrustManager, SSLUtil, TrustAllTrustManager}
import securesocial.core.providers.utils.{GravatarHelper, RoutesHelper}
import models.UUID
import securesocial.core._
import play.api.libs.json._

/**
  * Login class for checking if User is still logged through the LDAP.
  */
class Login extends SecuredController {
  def isLoggedIn() = Action { implicit request =>
    val result = for (
      authenticator <- SecureSocial.authenticatorFromRequest(request);
      identity <- UserService.find(authenticator.identityId)
    ) yield {
      // we should be able to use the authenticator.timedOut directly but it never returns true
      identity
    }

    result match {
      case Some(a) => Ok("yes")
      case None => Ok("no")
    }
  }

  def ldap(redirecturl: String, token: String) = Action{ implicit request =>
    val conf = current.configuration
    val provider = conf.getString("securesocial.ldap.provider").getOrElse("LDAP")
    Ok(views.html.ss.ldap(redirecturl, token, provider))
  }

  def ldapAuthenticate(uid: String, password: String)= Action { implicit request =>
    var ldapConnection: LDAPConnection = null
    val trustManager = if (LdapProvider.trustAllCertificates) new TrustAllTrustManager() else JVMDefaultTrustManager.getInstance()
    val sslUtil: SSLUtil = new SSLUtil(trustManager)
    val socketFactory: SSLSocketFactory = sslUtil.createSSLSocketFactory()

      try {
        // Create LDAP connection
        ldapConnection = new LDAPConnection(socketFactory, LdapProvider.hostname, LdapProvider.port)

        // Bind user to the connection.
        // This will throw an exception if the user credentials do not match any LDAP entry.
        // This exception is later caught to refuse access to the user.
        ldapConnection.bind("uid=" + uid + "," + LdapProvider.baseUserNamespace, password)

        // Filter to search the user's membership in the specified group
        val searchFilter: com.unboundid.ldap.sdk.Filter = com.unboundid.ldap.sdk.Filter.create("(&(objectClass=" + LdapProvider.objectClass +
          ")(memberOf=cn=" + LdapProvider.group + ","
          + LdapProvider.baseGroupNamespace + ")(uid=" + uid + "))")

        // Perform group membership search
        val searchResult: SearchResult = ldapConnection.search(LdapProvider.baseDN, SearchScope.SUB, searchFilter)

        // User is part of the specified group
        if (searchResult.getEntryCount == 1) {
          val entry = searchResult.getSearchEntries().get(0)

          Ok(Json.obj("user" -> Json.obj(
            "userId" -> LdapProvider.ldap,
            "firstName" -> entry.getAttributeValue("givenName"),
            "lastName" -> entry.getAttributeValue("sn"),
            "fullName" -> entry.getAttributeValue("cn"),
            "email" -> entry.getAttributeValue("mail"),
            "active" -> true
          )
          ))
        }
        // User is not part of the specified group
        else {
          NotFound
        }
      }
      catch {
        case exception: LDAPException =>
          exception.getResultCode match {
            case ResultCode.CONNECT_ERROR =>
              BadRequest("An error occurred while connecting to LDAP server. Please try again later.")
            case ResultCode.OPERATIONS_ERROR =>
              BadRequest("Invalid username or password.")
            case _ =>
              BadRequest("An unknown LDAP error occurred. Please try again later.")
          }
      }
      finally {
        // Close connection
        if (ldapConnection != null)
          ldapConnection.close()
      }
  }
}