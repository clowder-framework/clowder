(function ($, Configuration) {
  console.log("3D WebGL previewer for " + Configuration.id);

var width = 750;
var height = 550;

$(Configuration.tab).append("<table><tr><td>Left Button / Left Button + Shift</td><td>&nbsp;&nbsp;&nbsp;&nbsp;Rotate</td></tr>"
		+ "<tr><td>Mid Button / Left Button + Ctl</td><td>&nbsp;&nbsp;&nbsp;&nbsp;Pan</td></tr>"
		+ "<tr><td>Wheel</td><td>&nbsp;&nbsp;&nbsp;&nbsp;Zoom</td></tr></table>"); 

var s = document.createElement("script");
s.type = "text/javascript";
s.src = Configuration.previewer + "/lazyload-min.js";
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
		LazyLoad.js(Configuration.previewer + "/webGL.js", function() {
			init_webGL(resp, Configuration.previewer + "/../../../images");
		});		
	}	
}

$.ajax({
    url: Configuration.url,
    success: onDownloadResponse
});
   
}(jQuery, Configuration));