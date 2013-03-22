(function ($, Configuration) {
  console.log("3D WebGL previewer for " + Configuration.id);
    
var fileUrl = "http://" + Configuration.hostIp + ":" + window.location.port + Configuration.url;
var pathJs = "http://" + Configuration.hostIp + ":" + window.location.port + Configuration.jsPath + "/";
var pathImgs = "http://" + Configuration.hostIp + ":" + window.location.port + Configuration.imagesPath + "/";

var width = 750;
var height = 550;

var s = document.createElement("script");
s.type = "text/javascript";
s.src = pathJs + "lazyload-min.js";
console.log("Updating tab " + Configuration.tab);
$(Configuration.tab).append(s);

$(Configuration.tab).append(
        "<CANVAS id='c' width='" + width + "' height='" + height + "' style='border: 1px solid rgb(136, 136, 136);'>If you are seeing this, " +
        "your browser does not support <a href='http://www.google.com/chrome/'>" +
        "HTML5</a></CANVAS>" + "<div id='info'></div>");

function onDownloadResponse(resp){
	
	if (resp.error) {
        // e.g. the URL is malformed or the service is down
  	  $(Configuration.tab).append(
   		     "<b>File download ERROR: "+ resp.error + "</b>"
   		  );
        return;
    }
	else{		
		// initialize WebGL application
		LazyLoad.js(pathJs + "webGL.js", function() {
			init_webGL(resp, pathImgs);
		});		
	}	
}

$.ajax({
    url: fileUrl,
    success: onDownloadResponse
});
   
}(jQuery, Configuration));