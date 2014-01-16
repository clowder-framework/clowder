package services

import java.io.BufferedWriter
import java.io.FileWriter
import play.Logger
import org.apache.http.HttpResponse
import org.apache.http.client.methods.HttpPost
import org.apache.http.entity.mime.HttpMultipartMode
import org.apache.http.entity.mime.MultipartEntity
import org.apache.http.entity.mime.content.FileBody
import org.apache.http.impl.client.DefaultHttpClient
import org.apache.http.util.EntityUtils
import org.apache.http.entity.mime.content.StringBody
import java.nio.charset.Charset
import play.api.Play.current
import java.util.ArrayList
import org.apache.http.NameValuePair
import org.apache.http.message.BasicNameValuePair
import org.apache.http.client.entity.UrlEncodedFormEntity
import play.api.Configuration
import models.Dataset
import org.bson.types.ObjectId

trait FourStore {

  def addFileToGraph(fileId: String, selectedGraph:String = "rdfXMLGraphName"): Null = {
    	
		val queryUrl = play.api.Play.configuration.getString("rdfEndpoint").getOrElse("") + "/data/"
		val graphName = play.api.Play.configuration.getString(selectedGraph).getOrElse("")
        val httpclient = new DefaultHttpClient()
        val httpPost = new HttpPost(queryUrl)
                
        val urlParameters = new ArrayList[NameValuePair]()
        var updateQuery = "<http://" + play.Play.application().configuration().getString("hostIp").replaceAll("/$", "") + ":" + play.Play.application().configuration().getString("http.port") +"/api/files/" + fileId
        updateQuery = updateQuery + "> <http://www.w3.org/1999/02/22-rdf-syntax-ns#type> <http://www.cidoc-crm.org/rdfs/cidoc_crm_v5.0.2.rdfs#E31_Document> ."        
        
        Logger.debug("the query: "+updateQuery)
	    urlParameters.add(new BasicNameValuePair("data", updateQuery))
	    urlParameters.add(new BasicNameValuePair("graph", graphName))
	    urlParameters.add(new BasicNameValuePair("mime-type", "application/x-turtle"))
                
        httpPost.setEntity(new UrlEncodedFormEntity(urlParameters))
        val queryResponse = httpclient.execute(httpPost)
        Logger.info(queryResponse.getStatusLine().toString())
        
        val resultsEntity = queryResponse.getEntity()
        val resultsString = EntityUtils.toString(resultsEntity)
        
        Logger.debug("the results: "+resultsString)
    
        return null	
  }
  
  def addDatasetToGraph(datasetId: String, selectedGraph:String = "rdfXMLGraphName"): Null = {
    
		val queryUrl = play.api.Play.configuration.getString("rdfEndpoint").getOrElse("") + "/data/"
		val graphName = play.api.Play.configuration.getString(selectedGraph).getOrElse("")
        val httpclient = new DefaultHttpClient()
        val httpPost = new HttpPost(queryUrl)
                
        val urlParameters = new ArrayList[NameValuePair]()
        var updateQuery = "<http://" + play.Play.application().configuration().getString("hostIp").replaceAll("/$", "") + ":" + play.Play.application().configuration().getString("http.port") +"/api/datasets/" + datasetId
        updateQuery = updateQuery + "> <http://www.w3.org/1999/02/22-rdf-syntax-ns#type> <http://www.cidoc-crm.org/rdfs/cidoc_crm_v5.0.2.rdfs#E31_Document> ."
        
        Logger.debug("the query: "+updateQuery)
	    urlParameters.add(new BasicNameValuePair("data", updateQuery))
	    urlParameters.add(new BasicNameValuePair("graph", graphName))
	    urlParameters.add(new BasicNameValuePair("mime-type", "application/x-turtle"))
                
        httpPost.setEntity(new UrlEncodedFormEntity(urlParameters))
        val queryResponse = httpclient.execute(httpPost)
        Logger.info(queryResponse.getStatusLine().toString())
        
        val resultsEntity = queryResponse.getEntity()
        val resultsString = EntityUtils.toString(resultsEntity)
        
        Logger.debug("the results: "+resultsString)

		return null
  }
  
  def linkFileToDataset(fileId: String, datasetId: String, selectedGraph:String = "rdfXMLGraphName"): Null = {
    
		val queryUrl = play.api.Play.configuration.getString("rdfEndpoint").getOrElse("") + "/data/"
		val graphName = play.api.Play.configuration.getString(selectedGraph).getOrElse("")
        val httpclient = new DefaultHttpClient()
        val httpPost = new HttpPost(queryUrl)
                
        val urlParameters = new ArrayList[NameValuePair]()
        val hostIp = play.Play.application().configuration().getString("hostIp").replaceAll("/$", "") + ":" + play.Play.application().configuration().getString("http.port")
        var updateQuery = "<http://" + hostIp +"/api/datasets/" + datasetId
        updateQuery = updateQuery + "> <http://www.cidoc-crm.org/rdfs/cidoc_crm_v5.0.2.rdfs#P148_has_component> <http://"+ hostIp +"/api/files/" + fileId + "> ."
        
        Logger.debug("the query: "+updateQuery)
	    urlParameters.add(new BasicNameValuePair("data", updateQuery))
	    urlParameters.add(new BasicNameValuePair("graph", graphName))
	    urlParameters.add(new BasicNameValuePair("mime-type", "application/x-turtle"))
                
        httpPost.setEntity(new UrlEncodedFormEntity(urlParameters))
        val queryResponse = httpclient.execute(httpPost)
        Logger.info(queryResponse.getStatusLine().toString())
        
        val resultsEntity = queryResponse.getEntity()
        val resultsString = EntityUtils.toString(resultsEntity)
        
        Logger.debug("the results: "+resultsString)

		return null
  }
  
  def removeFileFromGraph(fileId: String, selectedGraph:String = "rdfXMLGraphName"): Null = {
    
        val queryUrl = play.api.Play.configuration.getString("rdfEndpoint").getOrElse("")  + "/update/"
        val graphName = play.api.Play.configuration.getString(selectedGraph).getOrElse("")
        val httpclient = new DefaultHttpClient()
        val hostIp = play.Play.application().configuration().getString("hostIp").replaceAll("/$", "") + ":" + play.Play.application().configuration().getString("http.port")
               
        removeFileMetadata(fileId, selectedGraph)
                
        val httpPost = new HttpPost(queryUrl)
        val urlParameters = new ArrayList[NameValuePair]() 
        var updateQuery = "DELETE { ?s ?p <http://" + hostIp +"/api/files/" + fileId + "> }"
        updateQuery = updateQuery + "WHERE { ?s ?p <http://" + hostIp +"/api/files/" + fileId + "> }"
        if(!graphName.equals("")){
          updateQuery = "WITH <" + graphName + "> " + updateQuery
        }
        Logger.debug("the query: "+updateQuery)
	    urlParameters.add(new BasicNameValuePair("update", updateQuery))                
        httpPost.setEntity(new UrlEncodedFormEntity(urlParameters))
        
        val queryResponse = httpclient.execute(httpPost)
        Logger.info(queryResponse.getStatusLine().toString())
        val resultsEntity = queryResponse.getEntity()
        val resultsString = EntityUtils.toString(resultsEntity)        
        Logger.debug("the results: "+resultsString)

		return null
    
  }
  
  def detachFileFromDataset(fileId: String, datasetId: String, selectedGraph:String = "rdfXMLGraphName"): Null = {
    
		val queryUrl = play.api.Play.configuration.getString("rdfEndpoint").getOrElse("") + "/update/"
		val graphName = play.api.Play.configuration.getString(selectedGraph).getOrElse("")
        val httpclient = new DefaultHttpClient()
        val httpPost = new HttpPost(queryUrl)
                
        val urlParameters = new ArrayList[NameValuePair]()
        val hostIp = play.Play.application().configuration().getString("hostIp").replaceAll("/$", "") + ":" + play.Play.application().configuration().getString("http.port")
        var updateQuery = "DELETE { <http://" + hostIp +"/api/datasets/" + datasetId
        updateQuery = updateQuery + "> <http://www.cidoc-crm.org/rdfs/cidoc_crm_v5.0.2.rdfs#P148_has_component> <http://"+ hostIp +"/api/files/" + fileId + "> }"
        updateQuery = updateQuery + "WHERE { <http://" + hostIp +"/api/datasets/" + datasetId
        updateQuery = updateQuery + "> <http://www.cidoc-crm.org/rdfs/cidoc_crm_v5.0.2.rdfs#P148_has_component> <http://"+ hostIp +"/api/files/" + fileId + "> }"
        if(!graphName.equals("")){
          updateQuery = "WITH <" + graphName + "> " + updateQuery
        }
        Logger.debug("the query: "+updateQuery)
	    urlParameters.add(new BasicNameValuePair("update", updateQuery))
                
        httpPost.setEntity(new UrlEncodedFormEntity(urlParameters))
        val queryResponse = httpclient.execute(httpPost)
        Logger.info(queryResponse.getStatusLine().toString())
        
        val resultsEntity = queryResponse.getEntity()
        val resultsString = EntityUtils.toString(resultsEntity)
        
        Logger.debug("the results: "+resultsString)

		return null
    
  }
  
  def removeDatasetFromGraph(datasetId: String, selectedGraph:String = "rdfXMLGraphName"): Null = {
    
        val queryUrl = play.api.Play.configuration.getString("rdfEndpoint").getOrElse("") + "/update/"
        val graphName = play.api.Play.configuration.getString(selectedGraph).getOrElse("")
        val httpclient = new DefaultHttpClient()
        val hostIp = play.Play.application().configuration().getString("hostIp").replaceAll("/$", "") + ":" + play.Play.application().configuration().getString("http.port")
        
        //First, delete all RDF links having to do with files belonging only to the dataset to be deleted, as those files will be deleted together with the dataset
        Services.datasets.get(datasetId) match{
          case Some(dataset)=> {
                var filesString = "" 
	            for(f <- dataset.files){
				      var notTheDataset = for(currDataset<- Dataset.findByFileId(f.id) if !dataset.id.toString.equals(currDataset.id.toString)) yield currDataset
				      if(notTheDataset.size == 0){				        
				        removeFileFromGraph(f.id.toString, selectedGraph)
				      }			    	
				    }                
	        
	        //Then, delete the dataset itself
	        var httpPost = new HttpPost(queryUrl)
	        var urlParameters = new ArrayList[NameValuePair]() 
	        var updateQuery = "DELETE { <http://" + hostIp +"/api/datasets/" + datasetId
	        updateQuery = updateQuery + "> ?p ?o } WHERE { <http://" + hostIp +"/api/datasets/" + datasetId
	        updateQuery = updateQuery + "> ?p ?o }"
	        if(!graphName.equals("")){
	        	updateQuery = "WITH <" + graphName + "> " + updateQuery
	        }
	        Logger.debug("the query: "+updateQuery)
		    urlParameters.add(new BasicNameValuePair("update", updateQuery))                
	        httpPost.setEntity(new UrlEncodedFormEntity(urlParameters))
	        
	        var queryResponse = httpclient.execute(httpPost)
	        Logger.info(queryResponse.getStatusLine().toString())
	        var resultsEntity = queryResponse.getEntity()
	        var resultsString = EntityUtils.toString(resultsEntity)        
	        Logger.debug("the results: "+resultsString)
          }
        }

		return null
    
  }
  
  def removeFileMetadata(fileId: String, selectedGraph:String = "rdfCommunityGraphName") : Null = {
    
		val queryUrl = play.api.Play.configuration.getString("rdfEndpoint").getOrElse("")  + "/update/"
        val graphName = play.api.Play.configuration.getString(selectedGraph).getOrElse("")
        val httpclient = new DefaultHttpClient()
        val hostIp = play.Play.application().configuration().getString("hostIp").replaceAll("/$", "") + ":" + play.Play.application().configuration().getString("http.port")
        
        
        var httpPost = new HttpPost(queryUrl)               
        var urlParameters = new ArrayList[NameValuePair]()        
        var updateQuery = "DELETE { <http://" + hostIp +"/api/files/" + fileId
        updateQuery = updateQuery + "> ?p ?o } WHERE { <http://" + hostIp +"/api/files/" + fileId
        updateQuery = updateQuery + "> ?p ?o }"
        if(!graphName.equals("")){
          updateQuery = "WITH <" + graphName + "> " + updateQuery
        }
        Logger.debug("the query: "+updateQuery)
        urlParameters.add(new BasicNameValuePair("update", updateQuery))
        httpPost.setEntity(new UrlEncodedFormEntity(urlParameters))
        
        var queryResponse = httpclient.execute(httpPost)
        Logger.info(queryResponse.getStatusLine().toString())
        var resultsEntity = queryResponse.getEntity()
        var resultsString = EntityUtils.toString(resultsEntity)        
        Logger.debug("the results: "+resultsString)
        
		return null
    
  }
  
//  def uploadToGraph(rdfFile: java.io.File): Null = {
//		val httpclient = new DefaultHttpClient()
//		val httpPost = new HttpPost(play.Play.application().configuration().getString("rdfUploadEndpoint"))
//		val entity = new MultipartEntity(HttpMultipartMode.BROWSER_COMPATIBLE)
//		entity.addPart("File", new FileBody(rdfFile, "application/rdf+xml"))
//		httpPost.setEntity(entity)
//		var rdfUploadResponse : HttpResponse = null
//		try {
//			rdfUploadResponse = httpclient.execute(httpPost)
//		} catch{
//		  case e: Exception => {
//				e.printStackTrace()
//				Logger.error("Couldn't add uploaded file to RDF triple store.")
//				EntityUtils.consume(entity)
//				return null
//			}
//		}
//		val statusLine = rdfUploadResponse.getStatusLine().toString()
//		Logger.info(statusLine)
//		if(statusLine.indexOf("201") == -1 && statusLine.indexOf("200") == -1){
//			Logger.error("Couldn't add uploaded file to RDF triple store.")
//			EntityUtils.consume(entity)
//			return null
//		}
//		Logger.info("Uploaded file added to RDF store.")
//		EntityUtils.consume(entity)
//		return null
//	}
  
  def sparqlQuery(queryText: String): String = {
    
	    val queryUrl = play.api.Play.configuration.getString("rdfEndpoint").getOrElse("") + "/sparql/"
        val httpclient = new DefaultHttpClient()
	    Logger.info("query text: "+ queryText)
        val httpPost = new HttpPost(queryUrl)
                
        val urlParameters = new ArrayList[NameValuePair]()
	    urlParameters.add(new BasicNameValuePair("query", queryText))
                
        httpPost.setEntity(new UrlEncodedFormEntity(urlParameters))
        val queryResponse = httpclient.execute(httpPost)
        Logger.info(queryResponse.getStatusLine().toString())
        
        val resultsEntity = queryResponse.getEntity()
        val resultsString = EntityUtils.toString(resultsEntity)
    
        return resultsString
  }
  
  def addFromFile(fileId: String, tempFile: java.io.File, selectedGraph:String = "rdfCommunityGraphName") : Null = {
    
    val r = Runtime.getRuntime()
    var tempDir = System.getProperty("java.io.tmpdir")
    if (new Character(tempDir.charAt(tempDir.length()-1)).toString().equals(System.getProperty("file.separator")) == false){
	    	tempDir = tempDir + System.getProperty("file.separator")
	    }
    
    val rdfEndpoint = play.api.Play.configuration.getString("rdfEndpoint").getOrElse("") + "/data"
    var rdfGraphName = play.api.Play.configuration.getString(selectedGraph).getOrElse("")
    if(!rdfGraphName.equals("")){
      rdfGraphName = "/" + rdfGraphName
    }

    val cmd = "curl -T " + "\"" +  tempDir +  tempFile.getName() + "\" \"" + rdfEndpoint + rdfGraphName + "\"" 	    
    val p = r.exec(cmd)		
    val outputGobbler = new util.StreamGobblerReturnsNotUploaded(p.getInputStream(), "INFO")
    val errorGobbler = new util.StreamGobblerReturnsNotUploaded(p.getErrorStream(),"ERROR")
    outputGobbler.start()
    errorGobbler.start()
    try {
    	p.waitFor()
    } catch {case ex: InterruptedException => Logger.error(ex.getMessage())}
    if(outputGobbler.wasSuccessful() || errorGobbler.wasSuccessful()){
	    	Logger.info("RDF uploaded to RDF store.")
	    }
	    else{
	    	Logger.error("Could not upload RDF to RDF store.")
	    }
    
    return null
  }
  
  
  
}