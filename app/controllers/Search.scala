package controllers

import play.api.mvc._
import services.ElasticsearchPlugin
import play.Logger
import scala.collection.JavaConversions.mapAsScalaMap
import services.Services
import sys.process._
import java.net.URL
import java.io.File
import scala.io.Source
import java.io._
import play.api.libs.ws.WS
import models.SectionDAO
import org.bson.types.ObjectId
import models.PreviewDAO
/**
 * Text search.
 * 
 * @author Luigi Marini
 */
object Search extends Controller{
  
  /**
   * Search results.
   */
  def search(query: String) = Action {
    Logger.debug("Searching for: " + query)
    import play.api.Play.current
    val result = current.plugin[ElasticsearchPlugin].map{_.search("files", query)}
    for (hit <- result.get.hits().hits()) {
      Logger.debug("Search result: " + hit.getExplanation())
      Logger.info("Fields: ")
      for ((key,value) <- mapAsScalaMap(hit.getFields())) {
        Logger.info(value.getName + " = " + value.getValue())
      }
      Services.files.getFile(hit.getId())
    }
    val files = result.get.hits().hits().map(hit => Services.files.getFile(hit.getId()).get)
    Ok(views.html.searchResults(query, files))
  }
  
  def multimediasearch()=Action{
    Logger.debug("Starting multimedia serach interface")
    Ok(views.html.multimediasearch())
    //Ok("Sucessful")
  }
  
  /**
   * Search MultimediaFeatures.
   */
  def searchMultimediaIndex(section_id: String) = Action {
    Logger.debug("Searching multimedia index")
    // TODO handle multiple previews found
    val preview = PreviewDAO.findBySectionId(new ObjectId(section_id))(0)
    Ok(views.html.searchMultimediaIndex(preview))
  }
  
  def advanced()=Action{
    Logger.debug("Starting Advanced Search interface")
    Ok(views.html.advancedsearch())
  }
  
 def SearchByText(query:String)=Action{
   Logger.debug("Searching for"+query)
   
   Ok(views.html.searchTextResults(query))
 }
 
 //GET the query image from the URL and compare within the database and show the result
 def searchbyURL(query:String)=Action{
   Logger.debug("Searching for"+query)
   
  // val result = WS.url(query).get() .map { response =>
  //   response.
  // }
   
  // var pic = scala.io.Source.fromURL(new URL(query))
  //#> new File("/tmp/pict")
  //new URL(query)  
   var slashindex=query.lastIndexOf('/')
   println(query.substring(slashindex+1))
   var qlength=query.length()
   var connect = (new URL(query)).openConnection()
   var input = connect.getInputStream
   //input.read()
   //input.close()
   //InputStream inputStream = new FileInputStream("c:\\file.xml");
    // write the inputStream to a FileOutputStream
	//var out=new FileOutputStream(new File("/tmp/about.jpg"))
     //val id =Services.files.save(new FileInputStream((new URL(query)).openConnection()),query.substring(slashindex+1))
   var file=new File("/tmp/"+query.substring(slashindex+1))
   
   var out=new FileOutputStream(file)
 
	var read1:Int = 0
	var bytes= new Array[Byte](1024*120)
	println(input.available())
	
	//while ((read1=input.read(bytes)) != -1) {
	  while (read1 != -1) {
		  read1 = input.read(bytes)
		  println(read1)
	  if(read1 != -1)
	  	 out.write(bytes, 0, read1)
	  else
	    read1= -1
	  	
	}
 
		//inputStream.close();
		out.flush()
		out.close()
 
		println("New file created!")
		 
		//var file1=Services.files.save(new FileInputStream("/tmp/"+query.substring(slashindex+1)),query.substring(slashindex+1),)
		//println(id)
    
		Ok(views.html.searchTextResults(query.substring(slashindex+1)))
   
 }
 
 def Filterby(id:String)=TODO
 
 def uploadquery() = Action(parse.multipartFormData) { request =>
  request.body.file("picture").map { picture =>
    import java.io.File
    val filename = picture.filename 
    val contentType = picture.contentType
    picture.ref.moveTo(new File("/tmp/picture"))
    Ok("File uploaded")
  }.getOrElse {
    Redirect(routes.Application.index).flashing(
      "error" -> "Missing file"
    )
  }
}
   /*Action(parse.multipartFormData) { request =>
  request.body.file("picture").map { picture =>
    import java.io.File
    val filename = picture.filename 
    val contentType = picture.contentType
    picture.ref.moveTo(new File("/tmp/picture"))
    Ok("File uploaded")
  }.getOrElse {
    Redirect(routes.Application.index).flashing(
      "error" -> "Missing file"
    )
  }
}*/
}