package services

import play.api.{ Plugin, Logger, Application }

import java.util.Date
import play.api.libs.json.Json
import play.api.Play.current
import play.api.libs.ws.WS
 import play.api.libs.concurrent.Promise
 import java.io._
import models.FileMD
import play.api.Logger
import play.api.Play.current
import play.api.data.Form
import play.api.data.Forms._

import play.api.mvc._
import services._
import play.api.libs.concurrent.Execution.Implicits._

import play.api.libs.iteratee.Input.{El, EOF, Empty}
import com.mongodb.casbah.gridfs.GridFS


import play.api.libs.json.JsValue
import play.api.libs.json.Json

import java.util.Date
import models.File
import org.bson.types.ObjectId
import com.mongodb.casbah.Imports._
import play.api.libs.json.Json._



import scala.concurrent.{future, blocking, Future, Await}
import scala.concurrent.ExecutionContext.Implicits.global

import play.libs.Akka
import akka.actor.Props
import akka.actor.Actor
import akka.actor.ActorRef

/*Versus Plugin
 * 
 * @author Smruti
 * 
 * */

class VersusPlugin(application:Application) extends Plugin{
  
  override def onStart() {
    Logger.debug("Starting Versus Plugin")
    
  }
  
  def index(id:String){
    
    val configuration = play.api.Play.configuration
    val client = configuration.getString("versus.client").getOrElse("")
    val indexId=configuration.getString("versus.index").getOrElse("")
    val urlf= client+"/files/"+id+"/blob"
    val host=configuration.getString("versus.host").getOrElse("")
    
     val indexurl=host+"/index/"+indexId+"/add"
    
    WS.url(indexurl).post(Map("infile" -> Seq(urlf))).map{
    	res=> 
    	Logger.debug("res.body"+res.body)
        		      		
    }
  }
  
   def build(){
    val configuration = play.api.Play.configuration
    val indexId=configuration.getString("versus.index").getOrElse("")
    val host=configuration.getString("versus.host").getOrElse("")
    val buildurl=host+"/index/"+indexId+"/build"
        
    WS.url(buildurl).get().map{
    	r=>Logger.debug("r.body"+r.body);
     }
    
  }
  
//  def query(id:String):scala.concurrent.Future[play.api.libs.ws.Response]= {
   
  def query(id:String):scala.concurrent.Future[Array[(String,String,Double,String)]]={
    val configuration = play.api.Play.configuration
    val client = configuration.getString("versus.client").getOrElse("")
    val indexId=configuration.getString("versus.index").getOrElse("")
    val query= client+"/queries/"+id+"/blob"
    val host=configuration.getString("versus.host").getOrElse("")
      
    val queryurl=host+"/index/"+indexId+"/query" 
   
    
        val resultFuture: scala.concurrent.Future[play.api.libs.ws.Response]= WS.url(queryurl).post(Map("infile" -> Seq(query)))
               
        resultFuture.map{
	        res=>
		        val json: JsValue=Json.parse(res.body)
		        val simvalue=json.as[Seq[models.Result.Result]]
		        
		        val l=simvalue.length
		        val ar=new Array[String](l)
		        val se=new Array[(String,String,Double,String)](l)
		        var i=0
		        simvalue.map{
		        	 s=>
		        	  val a=s.docID.split("/")
		        	  val n=a.length-2
		        	  Services.files.getFile(a(n)) match{
		        	  case Some(file)=>{
		        	    se.update(i,(a(n),s.docID,s.proximity,file.filename))
		        	    ar.update(i, file.filename)
		        	    Logger.debug("i"+i +" name="+ar(i)+"se(i)"+se(i)._3)
		        	    i=i+1
		        	   }
		        	 case None=>None
		        		         
		        	 }
		        		         		    
		        }
	          se
          }
        	 
     }
  
  
 def queryURL(url:String):scala.concurrent.Future[Array[(String,String,Double,String)]]={
    val configuration = play.api.Play.configuration
   // val client = configuration.getString("versus.client").getOrElse("")
    val indexId=configuration.getString("versus.index").getOrElse("")
    val query= url
    val host=configuration.getString("versus.host").getOrElse("")
    val queryurl=host+"/index/"+indexId+"/query" 
   
    
        val resultFuture: scala.concurrent.Future[play.api.libs.ws.Response]= WS.url(queryurl).post(Map("infile" -> Seq(query)))
               
        resultFuture.map{
	        res=>
		        val json: JsValue=Json.parse(res.body)
		        val simvalue=json.as[Seq[models.Result.Result]]
		        
		        val l=simvalue.length
		        val ar=new Array[String](l)
		        val se=new Array[(String,String,Double,String)](l)
		        var i=0
		        simvalue.map{
		        	 s=>
		        	  val a=s.docID.split("/")
		        	  val n=a.length-2
		        	  Services.files.getFile(a(n)) match{
		        	  case Some(file)=>{
		        	    se.update(i,(a(n),s.docID,s.proximity,file.filename))
		        	    ar.update(i, file.filename)
		        	    Logger.debug("i"+i +" name="+ar(i)+"se(i)"+se(i)._3)
		        	    i=i+1
		        	   }
		        	 case None=>None
		        		         
		        	 }
		        		         		    
		        }
	          se
          }
        	 
     }
  
  
  override def onStop(){
    Logger.debug("Shutting down Versus Plugin")
  }
}

