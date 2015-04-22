(function ($, Configuration) {
  console.log("video previewer for " + Configuration.id);
  
  var hostAddress = window.location.protocol + "//" + window.location.hostname + (window.location.port ? ':' + window.location.port: '');
  var pathJs = hostAddress + Configuration.jsPath + "/";
  
  var useTab = Configuration.tab;
  var referenceUrl = Configuration.url;
  var confId = Configuration.id;
  var fileId = Configuration.fileid;
  
  var s = document.createElement("script");
  s.type = "text/javascript";
  s.src = pathJs + "popcorn-complete.min.js";
  console.log("Updating tab " + useTab);
  $(useTab).append(s);
  $(useTab).append("<br/>");
  
  //Showing the original file. 
  //This also means the "preview" is a single video and not a multi-option cross-browser compatibility combination, as those can only be generated as previews by the system. 
  //Thus, show the file as a single video.
  if(confId == fileId){
	  $(useTab).append(			  
			     "<video width='750px' id='ourvideo' controls><source src='" + referenceUrl + "'></source></video>"
			  );
  }
  else{	//Showing a preview, so have to check if it is a multi-option cross-browser compatibility combination (new video extractor versions)
	  //or a single video (old version of the video extractor). For previews, this can be done by getting their preview metadata.
	  var request = $.ajax({
	       type: 'GET',
	       url: jsRoutes.api.Previews.getMetadata(fileId).url,
	       contentType: "application/json"
	     });
	  request.done(function (respJSON){
	        console.log("Response " + respJSON);
	        if(respJSON.contentType == "video/videoalternativeslist"){
	        	$.ajax({
	        	    url: referenceUrl,
	        	    async:true,
	        	    success: function (data) {
	        	    	  var videosIds = data.split("\n");	        	    		
	        	    	  $(useTab).append(			  
	        	    	     "<video width='750px' id='ourvideo' controls>" +
	        	    	     		"<source src='" + jsRoutes.api.Previews.download(videosIds[0]).url  + "' type='video/mp4'></source>" +
	        	    	     		"<source src='" + jsRoutes.api.Previews.download(videosIds[1]).url  + "' type='video/webm'></source>"+
	        	    	     		"<p>Your browser cannot play MP4 or WebM (maybe no codex), cannot play video.</p>"+
	        	    	     "</video>"
	        	    	  );
	        	    	 },
	        	    	 error: function(jqXHR, textStatus, errorThrown) { 
	        	    	        alert("Status: " + textStatus); alert("Error: " + errorThrown); 
	        	    	    },
	        	    dataType: 'text'
	        	});
	        }
	        else{
	        	$(useTab).append(			  
	   			     "<video width='750px' id='ourvideo' controls><source src='" + referenceUrl + "'></source></video>"
	   			  );
	        }
	  });
	  request.fail(function (jqXHR, textStatus, errorThrown){
  		console.error(
      		"The following error occured: "+
      		textStatus, errorThrown		            
  			);
  		alert("ERROR: " + errorThrown +". Cannot retrieve exact preview type. Aborting video display." );
	   });
  }
  
     
}(jQuery, Configuration));