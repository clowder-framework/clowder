package controllers

import play.api.mvc.Request
import models.Dataset
import models.Collection
import org.apache.commons.lang.StringEscapeUtils

object Utils {
  /**
   * Return base url given a request. This will add http or https to the front, for example
   * https://localhost:9443 will be returned if it is using https.
   */
  def baseUrl(request: Request[Any]) = {
    val httpsPort = System.getProperties().getProperty("https.port", "")
    val protocol = if (httpsPort == request.host.split(':').last)  "https" else "http"
    protocol + "://" + request.host
  }

  /**
   * Returns protocol in request stripping it of the : trailing character.
   * @param request
   * @return
   */
  def protocol(request: Request[Any]) = {
    val httpsPort = System.getProperties().getProperty("https.port", "")
    if (httpsPort == request.host.split(':').last)  "https" else "http"
  }
  
  /**
   * Utility method to modify the elements in a dataset that are encoded when submitted and stored. These elements
   * are decoded when a view requests the objects, so that they can be human readable.
   * 
   * Currently, the following dataset elements are encoded:
   * name
   * description
   *  
   */
  def decodeDatasetElements(dataset: Dataset) : Dataset = {
      val updatedName = updateEncodedTextNewlines(dataset.name)
      val updatedDesc = updateEncodedTextNewlines(dataset.description)
      dataset.copy(name = updatedName, description = updatedDesc)
  }
  
  /**
   * Utility method to modify the elements in a collection that are encoded when submitted and stored. These elements
   * are decoded when a view requests the objects, so that they can be human readable.
   * 
   * Currently, the following collection elements are encoded:
   * 
   * name
   * description
   *  
   */
  def decodeCollectionElements(collection: Collection) : Collection  = {
      val updatedName = updateEncodedTextNewlines(collection.name)
      val updatedDesc = updateEncodedTextNewlines(collection.description)
      collection.copy(name = updatedName, description = updatedDesc)
  }


  /**
   * Encoded text can have newlines. When displayed via a view, they must be translated into linebreaks
   * in order to render correctly.
   *
   * @param text The text to be updated with linebreaks
   * @return An updated String with newlines replaced.
   */
  def updateEncodedTextNewlines(text: String): String = {
    text.replace("\n", "<br>")
  }
}