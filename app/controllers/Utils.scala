package controllers

import java.net.URL
import models._
import play.api.data.format.Formatter
import play.api.data.{Mapping, Forms, FormError}
import play.api.mvc.{RequestHeader, Request}
import org.apache.commons.lang.StringEscapeUtils

import scala.collection.mutable.ListBuffer

object Utils {
  /**
   * Return base url given a request. This will add http or https to the front, for example
   * https://localhost:9443 will be returned if it is using https.
   */
  def baseUrl(request: Request[Any], absolute: Boolean = true) = {
    if (absolute) {
      routes.Files.list().absoluteURL(https(request))(request).replace("/files", "")
    } else {
      routes.Files.list().url.replace("/files", "")
    }
  }

  /**
    * Returns true if protocol is https
    */
  def https(request: RequestHeader): Boolean = {
    request.headers.get("x-forwarded-proto") match {
      case Some(p) => (p == "https")
      case None => {
        val httpsPort = System.getProperties().getProperty("https.port", "")
        httpsPort == request.host.split(':').last
      }
    }
  }

  /**
   * Utility method to modify the elements in a space that are encoded when submitted and stored. These elements
   * are decoded when a view requests the objects, so that they can be human readable.
   *
   * Currently, the following space elements are encoded:
   * name
   * description
   *
   */
  def decodeDatasetElements(dataset: Dataset) : Dataset = {
    val updatedName = updateEncodedTextNewlines(dataset.name)
    val updatedDesc = updateEncodedTextNewlines(dataset.description)
    dataset.copy(name = updatedName, description = updatedDesc)
  }

  def decodeSpaceElements(space: ProjectSpace): ProjectSpace = {
    val decodedName = StringEscapeUtils.unescapeHtml(space.name)
    val decodedDesc = StringEscapeUtils.unescapeHtml(space.description)
    space.copy(name = decodedName, description = decodedDesc)
  }

  def decodeVocabularyTermElements(vocabularyTerm : VocabularyTerm) = {
    val decodedKey = StringEscapeUtils.unescapeHtml(vocabularyTerm.key)
    val decodedDefaultValue = StringEscapeUtils.unescapeHtml(vocabularyTerm.default_value)
    val decodedUnits = StringEscapeUtils.unescapeHtml(vocabularyTerm.units)
    val decodedDescription = StringEscapeUtils.unescapeHtml(vocabularyTerm.description)
    vocabularyTerm.copy(key = decodedKey, default_value = decodedDefaultValue)
  }

  def decodeString(string : String) : String ={
    val decodedString = StringEscapeUtils.unescapeHtml(string).replace("\n"," ")

    return decodedString
  }

  /**
   * Default formatter for the `String` type.
   */
  implicit def stringFormat: Formatter[String] = new Formatter[String] {
    def bind(key: String, data: Map[String, String]) = data.get(key).toRight(Seq(FormError(key, "error.required", Nil)))
    def unbind(key: String, value: String) = Map(key -> value)
  }

  /**
   * Exact copy of private function in play.api.data.format.Formats
   */
  private def parsing[T](parse: String => T, errMsg: String, errArgs: Seq[Any])(key: String, data: Map[String, String]): Either[Seq[FormError], T] = {
    stringFormat.bind(key, data).right.flatMap { s =>
      scala.util.control.Exception.allCatch[T]
        .either(parse(s))
        .left.map(e => Seq(FormError(key, errMsg, errArgs)))
    }
  }

  /**
   * Default formatter for the `URL` type.
   */
  object CustomMappings {
    implicit def urlFormat: Formatter[URL] = new Formatter[URL] {
      override val format = Some(("format.url", Nil))
      def bind(key: String, data: Map[String, String]) = parsing(v => new URL(v), "Illegal Address", Nil)(key, data)
      def unbind(key: String, value: URL) = Map(key -> value.toString)
    }
    def urlType: Mapping[URL] = Forms.of[URL]
    implicit def uuidFormat: Formatter[UUID] = new Formatter[UUID] {
      override val format = Some(("format.uuid", Nil))
      def bind(key: String, data: Map[String, String]) = parsing(v => UUID(v), "error.uuid", Nil)(key, data)
      def unbind(key: String, value: UUID) = Map(key -> value.toString)
    }
    def uuidType: Mapping[UUID] = Forms.of[UUID]
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

  /*
   * Utility method to modify the elements in a comment that are encoded when submitted and stored. These elements
   * are decoded when a view requests the objects, so that they can be human readable. Called recursively in order
   * to easily decode all the replies associated with individual comments as well.
   *
   * Currently, the following comment elements are encoded:
   *
   * text
   * replies
   *
   * @param comment The comment to be HTML decoded
   * @return A copy of the original comment, with the specified elements decoded
   */
  def decodeCommentElements(comment: Comment) : Comment = {
    val updatedText = updateEncodedTextNewlines(comment.text)
    comment.copy(text = updatedText, replies = decodeCommentReplies(comment))
  }

  /**
   * Utility method to decode the replies within a comment. Makes a recursive call to decodeCommentElements that in turn
   * invokes this for nested replies.
   *
   * @param comment The original comment that is to be checked for replies that need to be decoded
   * @return A list of comments that represent top level replies for the original comment
   */
  def decodeCommentReplies(comment: Comment): List[Comment] = {
    var decodedReplies = ListBuffer.empty[Comment]
    if (comment.replies.isEmpty) {
      decodedReplies.toList
    }
    else {
      for (aReply <- comment.replies) {
        decodedReplies += decodeCommentElements(aReply)
      }
      decodedReplies.toList
    }
  }
}