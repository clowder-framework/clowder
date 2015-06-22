package services

import play.libs.Akka
import play.api.{ Plugin, Logger, Application }
import java.io._
import play.api.Play.{ current, configuration }
import play.api.libs.iteratee._
import play.api.libs.concurrent.Execution.Implicits._
import play.api.libs.ws._
import scala.concurrent._
import scala.concurrent.duration._
import com.ning.http.multipart.{ ByteArrayPartSource, Part, FilePart, MultipartRequestEntity }
import com.ning.http.client.FluentCaseInsensitiveStringsMap
import play.api.http.{ Writeable, ContentTypeOf }
import org.apache.commons.io.IOUtils
import com.ning.http.client.Realm.AuthScheme

/**
 * Polyglot Plugin
 *
 * @author Inna Zharnitsky 
 */
class PolyglotPlugin(application: Application) extends Plugin {

  val polyglotUser: Option[String] = configuration.getString("polyglot.username")
  val polyglotPassword: Option[String] = configuration.getString("polyglot.password")
  val polyglotConvertURL: Option[String] = configuration.getString("polyglot.convertURL")
  val polyglotInputsURL: Option[String] = configuration.getString("polyglot.inputsURL")

  val table = scala.collection.mutable.HashMap.empty[String, List[String]]

  override def onStart() {
    Logger.debug("Starting Polyglot Plugin")
  }

  override def onStop() {
    Logger.debug("Stopping Polyglot Plugin")
  }

  //using code from https://www.playframework.com/documentation/2.2.x/ScalaWS
  //Processing large responses
  def fromStream(stream: OutputStream): Iteratee[Array[Byte], Unit] = Cont {
    case e @ Input.EOF =>
      Logger.debug("fromStream case EOF")
      stream.close()
      Done((), e)
    case Input.El(data) =>
      Logger.debug("fromStream case input.El, data = " + data)
      stream.write(data)
      fromStream(stream)
    case Input.Empty =>
      Logger.debug("fromStream case empty , so calling fromStream again")
      fromStream(stream)
  }

  /**
   * Keeps checking for the file on Polyglot server until the file is found or until too many tries.
   */
  def checkForFileAndDownload(triesLeft: Int, url: String, outputStream: OutputStream): Future[Unit] =
    {
      if ( !polyglotUser.isDefined || !polyglotPassword.isDefined) {
        throw new RuntimeException("Polyglot credentials not defined.")
      }
      
      if (triesLeft == 0) Future.failed(throw new RuntimeException("Converted file not found."))
      
      else  WS.url(url).withAuth(polyglotUser.get,  polyglotPassword.get, AuthScheme.BASIC).get flatMap { res =>
        if (res.status == 200) {
          //this is the callback, runs after file exists is TRUE
          Logger.debug("File exists on polyglot. Will download now.")
          //file exists on Polyglot, begin download using iteratee
          //following example in https://www.playframework.com/documentation/2.2.x/ScalaWS  Processing large responses           
          val result = WS.url(url)
            .withAuth(polyglotUser.get, polyglotPassword.get, AuthScheme.BASIC)
            .get { xx => fromStream(outputStream) }
           	.flatMap(_.run)
          //Returning result. When it is mapped in the controller, the successful future is AFTER file has been downloaded on the Clowder server.
          result
        } else {
          Logger.debug("Checking if file exists on Polyglot, status = " + res.status + " " + res.statusText + ", call again in 3 sec")
          akka.pattern.after(3 seconds, using = Akka.system.scheduler)(checkForFileAndDownload((triesLeft - 1), url, outputStream))
        }
      }
    }

  /** 
   *  Uploads to Polyglot the file to be converted. Returns url of the converted file.
   */
  def getConvertedFileURL(filename: String, inputStream: java.io.InputStream, outputFormat: String): Future[String] = {
    //check that Polyglot credentials are defined
    if (!polyglotConvertURL.isDefined || !polyglotUser.isDefined || !polyglotPassword.isDefined) {
      throw new RuntimeException("Polyglot credentials not defined.")
    }

    //post a multipart form data to Polyglot.
    //based on the code from https://github.com/playframework/playframework/issues/902
    //comment dated Dec 5, 2014                   
    // Build up the Multiparts - consists of just one file part               
    val filePart: FilePart = new FilePart(filename, new ByteArrayPartSource(filename, IOUtils.toByteArray(inputStream)))
    val parts = Array[Part](filePart)
    val reqEntity = new MultipartRequestEntity(parts, new FluentCaseInsensitiveStringsMap)
    val baos = new ByteArrayOutputStream
    reqEntity.writeRequest(baos)
    val bytes = baos.toByteArray
    val reqContentType = reqEntity.getContentType

    // Now just send the data to the WS API                
    val response = WS.url(polyglotConvertURL.get + outputFormat)
      .withAuth(polyglotUser.get, polyglotPassword.get, AuthScheme.BASIC)
      .post(bytes)(Writeable.wBytes, ContentTypeOf(Some(reqContentType)))

    //get the url for the converted file on Polyglot  
    val fileURLFut = response.map {
      res =>
        if (res.status != 200) {
          Logger.debug("Could not get url of converted file - status = " + res.status + "  " + res.statusText)
          throw new RuntimeException("Could not connect to Polyglot. " + res.statusText)
        }
        //TODO: Find an easier way to get rid of html markup 
        //result is an html link
        //<a href=http://the_url_string>http://the_url_string</a>
        val fileURL = res.body.substring(res.body.indexOf("http"), res.body.indexOf(">"))
        fileURL
    }
    fileURLFut.map { url => Logger.debug("Converted file url =  " + url) }
    fileURLFut
  }
  
  /**
   * If outputs are stored locally, returns them. Otherwise, calls another method to fetch outputs from Polyglot.
   */
  def getOutputFormats(inputType: String): Future[Option[List[String]]] = {
    for ((k, v) <- table) Logger.debug("key: " + k)

    //check if outputs are already in the table, and that they are non-empty
    if ((table contains inputType) && (table(inputType).length > 0)) {
      Logger.debug("output formats for " + inputType + " already in table " + table(inputType))
      Future(Some(table(inputType)))
    } else {
      Logger.debug("output formats for " + inputType + " NOT in table")
      getOutputFormatsPolyglot(inputType)
    }
  }
  
  /**
   * Goes to Polyglot and fetches all output formats for the input format given.
   */
  def getOutputFormatsPolyglot(inputType: String): Future[Option[List[String]]] = {   
    //proceed only if received all the config params
    if (polyglotInputsURL.isDefined && polyglotUser.isDefined && polyglotPassword.isDefined) {
      //call polyglot server with authentication          
      WS.url(polyglotInputsURL.get + inputType)
        .withAuth(polyglotUser.get, polyglotPassword.get, AuthScheme.BASIC)
        .get
        .map {
          case response =>
            //If reponse was successful, get a list of output formats. Otherwise return None.
            val outputFormats = {
              if (response.status == 200) {
                Logger.debug("success getting response from Polyglot")
                val formatsList = response.body.split("\n").toList
                //save list of output formats in the table
                table(inputType) = formatsList
                Some(formatsList)
              } else {
                Logger.debug("Problems getting response from Polyglot, response status = " + response.status + ", " + response.statusText)
                None
              }
            }
            outputFormats
        }
    } else {
      Logger.debug("Config params not defined")
      Future(None)
    }
  }
}