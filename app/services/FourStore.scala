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

trait FourStore {

  def addFileToGraph(fileId: String): Null = {
    
	  	val rdfFile = java.io.File.createTempFile("newfilerdf_" + fileId + "_", ".rdf")
		val fileWriter =  new BufferedWriter(new FileWriter(rdfFile))	
		var theRDF = "<?xml version=\"1.0\" encoding=\"UTF-8\"?><rdf:RDF xmlns:rdf=\"http://www.w3.org/1999/02/22-rdf-syntax-ns#\" xmlns:crm=\"http://www.cidoc-crm.org/rdfs/cidoc_crm_v5.0.2.rdfs#\">"				
		theRDF = theRDF + "<rdf:Description rdf:about=\"" + play.Play.application().configuration().getString("hostIp").replaceAll("/$", "") +"/api/files/" + fileId
		theRDF = theRDF + "\"><rdf:type rdf:resource=\"" + "crm:E31_Document"
		theRDF = theRDF	+ "\"/></rdf:Description>"
		theRDF = theRDF	+ "</rdf:RDF>"
		fileWriter.write(theRDF)
		fileWriter.close()
		
		Logger.info("Adding uploaded file to RDF triple store.")
		
		uploadToGraph(rdfFile)
		
		rdfFile.delete()
		return null
  }
  
  def addDatasetToGraph(datasetId: String): Null = {
    
	  	val rdfFile = java.io.File.createTempFile("newdatasetrdf_" + datasetId + "_", ".rdf")
		val fileWriter =  new BufferedWriter(new FileWriter(rdfFile))	
		var theRDF = "<?xml version=\"1.0\" encoding=\"UTF-8\"?><rdf:RDF xmlns:rdf=\"http://www.w3.org/1999/02/22-rdf-syntax-ns#\" xmlns:crm=\"http://www.cidoc-crm.org/rdfs/cidoc_crm_v5.0.2.rdfs#\">"				
		theRDF = theRDF + "<rdf:Description rdf:about=\"" + play.Play.application().configuration().getString("hostIp").replaceAll("/$", "") +"/api/datasets/" + datasetId
		theRDF = theRDF + "\"><rdf:type rdf:resource=\"" + "crm:E31_Document"
		theRDF = theRDF	+ "\"/></rdf:Description>"
		theRDF = theRDF	+ "</rdf:RDF>"
		fileWriter.write(theRDF)
		fileWriter.close()
		
		Logger.info("Adding uploaded dataset to RDF triple store.")
		
		uploadToGraph(rdfFile)
		
		rdfFile.delete()
		return null
  }
  
  def linkFileToDataset(fileId: String, datasetId: String): Null = {
    
	    val rdfFile = java.io.File.createTempFile("newdatasetfilelinkrdf_" + datasetId + "_", ".rdf")
    	val fileWriter =  new BufferedWriter(new FileWriter(rdfFile))
    	var theRDF = "<?xml version=\"1.0\" encoding=\"UTF-8\"?><rdf:RDF xmlns:rdf=\"http://www.w3.org/1999/02/22-rdf-syntax-ns#\" xmlns:crm=\"http://www.cidoc-crm.org/rdfs/cidoc_crm_v5.0.2.rdfs#\">"				
		theRDF = theRDF + "<rdf:Description rdf:about=\"" + play.Play.application().configuration().getString("hostIp").replaceAll("/$", "") +"/api/datasets/" + datasetId
		theRDF = theRDF + "\"><P148_has_component xmlns=\"http://www.cidoc-crm.org/rdfs/cidoc_crm_v5.0.2.rdfs#\" rdf:resource=\"" + play.Play.application().configuration().getString("hostIp").replaceAll("/$", "") +"/api/files/" + fileId
		theRDF = theRDF	+ "\"/></rdf:Description>"
		theRDF = theRDF	+ "</rdf:RDF>"
		fileWriter.write(theRDF)
		fileWriter.close()
		
		Logger.info("Linking file with dataset in RDF triple store.")
		
		uploadToGraph(rdfFile)
		
		rdfFile.delete()
		return null
  }
  
  def uploadToGraph(rdfFile: java.io.File): Null = {
		val httpclient = new DefaultHttpClient()
		val httpPost = new HttpPost(play.Play.application().configuration().getString("rdfUploadEndpoint"))
		val entity = new MultipartEntity(HttpMultipartMode.BROWSER_COMPATIBLE)
		entity.addPart("File", new FileBody(rdfFile, "application/rdf+xml"))
		httpPost.setEntity(entity)
		var rdfUploadResponse : HttpResponse = null
		try {
			rdfUploadResponse = httpclient.execute(httpPost)
		} catch{
		  case e: Exception => {
				e.printStackTrace()
				Logger.error("Couldn't add uploaded file to RDF triple store.")
				EntityUtils.consume(entity)
				return null
			}
		}
		val statusLine = rdfUploadResponse.getStatusLine().toString()
		Logger.info(statusLine)
		if(statusLine.indexOf("201") == -1 && statusLine.indexOf("200") == -1){
			Logger.error("Couldn't add uploaded file to RDF triple store.")
			EntityUtils.consume(entity)
			return null
		}
		Logger.info("Uploaded file added to RDF store.")
		EntityUtils.consume(entity)
		return null
	}
  
  def sparqlSearch(queryText: String): String = {
    
	    val queryUrl = play.api.Play.configuration.getString("rdfSPARQLEndpoint").getOrElse("")
        val httpclient = new DefaultHttpClient()
        val httpPost = new HttpPost(queryUrl)
        val entity = new MultipartEntity(HttpMultipartMode.BROWSER_COMPATIBLE)
        entity.addPart("query", new StringBody(queryText, "text/plain",
                Charset.forName( "UTF-8" )))
        httpPost.setEntity(entity)
        val queryResponse = httpclient.execute(httpPost)
        Logger.info(queryResponse.getStatusLine().toString())
        
        val resultsEntity = queryResponse.getEntity()
        val resultsString = EntityUtils.toString(resultsEntity)
    
        return resultsString
  }
  
}