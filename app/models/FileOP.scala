package models
import scala.collection.mutable.MutableList
import services._
import javax.inject.{Inject, Singleton}
import play.api.libs.json.Json
import play.api.libs.json.JsValue
import play.api.Logger

object FileOP {
  /**
   * Returns extracted lists of tags for a file
   * 
   */
  
  val previews: PreviewService=DI.injector.getInstance(classOf[PreviewService])
  val files:FileService=DI.injector.getInstance(classOf[FileService])
  
  def extractTags(file: models.File) = {
    val tags = file.tags
    // Transform the tag list into a list of ["extractor_id" or "userId", "values"] items,
    // where "values" are the list of tag name values.
    var tagResE = Map[String, MutableList[String]]()
    tags.filter(_.extractor_id.isDefined).foreach(tag => {
      val ename = tag.extractor_id.get
      if (tagResE.contains(ename)) {
        tagResE.get(ename).get += tag.name
      } else {
        tagResE += ((ename, MutableList(tag.name)))
      }
    })
    val jtagsE = tagResE.map { case (k, v) => Json.obj("extractor_id" -> k, "values" -> Json.toJson(v)) }
    var tagResU = Map[String, MutableList[String]]()
    tags.filter(_.userId.isDefined).foreach(tag => {
      val ename = tag.userId.get
      if (tagResU.contains(ename)) {
        tagResU.get(ename).get += tag.name
      } else {
        tagResU += ((ename, MutableList(tag.name)))
      }
    })
    val jtagsU = tagResU.map { case (k, v) => Json.obj("userId" -> k, "values" -> Json.toJson(v)) }
    val jtags = jtagsE ++ jtagsU
    jtags
  }
  
  
  /**
   * Returns Previews extracted so far for a file
   * 
   */
  def extractPreviews(id: UUID) = {
    val previews1 =previews.findByFileId(id);
    // Transform the preview list into a list of ["extractor_id", "values"] items,
    // where "values" are the preview properties, such as "preview_id", "url", and "contentType".
    var previewRes = Map[String, MutableList[JsValue]]()
    previews1.filter(_.extractor_id.isDefined).foreach(p => {
    	
      val ename = p.extractor_id.get
      val jitem = Json.obj("preview_id" -> p.id.stringify,
        "contentType" -> p.contentType, "url" -> api.routes.Previews.download(p.id).toString)
      if (previewRes.contains(ename.toString)) {
        previewRes.get(ename.toString).get += jitem
      } else {
        previewRes += ((ename.toString, MutableList(jitem)))
      }
    })
    val jpreviews = previewRes.map { case (k, v) => Json.obj("extractor_id" -> k, "values" -> Json.toJson(v)) }
    jpreviews
  }
  
  
  
  /**
   * Returns Versus Descriptors from metadata field for a file
   * 
   */
  
  def extractVersusDescriptors(id:UUID): JsValue= {
    val vDes=files.getVersusMetadata(id)
    if(vDes==null){
       Json.arr()
    }else{
      vDes
    }
         
  }

}