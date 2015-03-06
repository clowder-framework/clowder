(function ($, Configuration) {
  console.log("video presentation previewer for " + Configuration.id);  
  console.log("Updating tab " + Configuration.tab);
  
  var hostAddress = window.location.protocol + "//" + window.location.hostname + (window.location.port ? ':' + window.location.port: '');
  var pathJs = hostAddress + Configuration.jsPath + "/";
  
  var useTab = Configuration.tab;
  var referenceUrl = Configuration.url;
  var confId = Configuration.id;
  
  var s = document.createElement("script");
  s.type = "text/javascript";
  s.src = pathJs + "popcorn-complete.js";
  $(useTab).append(s);
  
  $.ajax({
	    url: referenceUrl,
	    async:true,
	    success: function (data) {
	    	  var videosIds = data.split("\n");
	    		
	    	  $(useTab).append(			  
	    	     "<video width='750px' id='ourvideo' controls>" +
	    	     		"<source src='" + jsRoutes.api.Previews.download(videosIds[1]).url  + "' type='video/mp4'></source>" +
	    	     		"<source src='" + jsRoutes.api.Previews.download(videosIds[2]).url  + "' type='video/webm'></source>"+
	    	     		"<p>Your browser cannot play MP4 or WebM (maybe no codex), cannot play video.</p>"+
	    	     "</video>"
	    	  );
	    	  $(useTab).append("<br/>");
	    	  $(useTab).append("<a href='" + jsRoutes.api.Previews.download(videosIds[0]).url + "'>Download merged high-quality video.</a>");
	    	 },
	    	 error: function(jqXHR, textStatus, errorThrown) { 
	    	        alert("Status: " + textStatus); alert("Error: " + errorThrown); 
	    	    },
	    dataType: 'text'
	});
  

}(jQuery, Configuration));

