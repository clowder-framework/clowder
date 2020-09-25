package controllers

import play.api.{Configuration, Logger}
import play.api.Play.current
import play.api.libs.concurrent.Execution.Implicits._
import play.api.libs.json._
import services.{ExtractionRequestsService, ExtractorService}


class ExtractionInfo @Inject() (extractors: ExtractorService,
                                dtsrequests: ExtractionRequestsService,
                               config: Configuration) extends SecuredController {

/**
 * Directs currently running extractors information to the webpage 
 */
  def getExtractorNames() = AuthenticatedAction { implicit request =>

    config.get

    val list_names = extractors.getExtractorNames(List.empty)
    Ok(views.html.extractors(list_names, list_names.size))

  }
  
/**
 * Directs input type supported by currently running extractors information to the webpage
 */
  def getExtractorInputTypes() = AuthenticatedAction { implicit request =>
    val list_inputtypes = extractors.getExtractorInputTypes()
    var jarr = new JsArray()
    var list_inputtypes1 = List[String]()
    list_inputtypes.map {
      ls =>
        Logger.info("Extractor Input Type:  " + ls)
        jarr = jarr :+ JsString(ls)
        list_inputtypes1 = ls :: list_inputtypes1

    }
    Logger.debug("Json array for list of input types supported by extractors----" + jarr.toString)
    Ok(views.html.extractorsInputTypes(list_inputtypes1,list_inputtypes1.size))

  }
  
  /**
   * Directs DTS extractions requests information to the webpage
   */
   def getDTSRequests() = AuthenticatedAction { implicit request =>

    var list_requests = dtsrequests.getDTSRequests()
    var startTime = models.ServerStartTime.startTime
    var currentTime = Calendar.getInstance().getTime()
    Ok(views.html.extractionRequests(list_requests, list_requests.size, startTime, currentTime))
  }
   
   /**
   * DTS Bookmarklet page
   */
   def getBookmarkletPage() = AuthenticatedAction { implicit request =>

      Ok(views.html.dtsbookmarklet(Utils.baseUrl(request)))
  }

  /**
   * DTS Chrome Extension page
   */
  def getExtensionPage() = AuthenticatedAction { implicit request =>
    val url = Utils.baseUrl(request)
    var hostname = if (url.indexOf('.') == -1) { url.substring(url.indexOf('/') + 2, url.lastIndexOf(':')) } else { url.substring(url.indexOf('/') + 2, url.indexOf('.')) }
    Logger.debug(" url= " + url + "  hostname " + hostname)
    val extensionHostUrl = config.get[Option[String]]("dts.extension.host")
    Ok(views.html.dtsExtension(Utils.baseUrl(request), hostname, extensionHostUrl))
  }
}