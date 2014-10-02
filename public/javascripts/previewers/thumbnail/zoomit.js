function onZoomitResponse(resp) {
	if (resp.error) {
		// e.g. the URL is malformed or the service is down
		$(Configuration.tab).append("<b>Zoom.it ERROR: "+ resp.error + "</b>");
		return;
	}

	var content = resp.content;

	// Create a closure to keep the old document.write private 
	(function () {
		var oldDW = document.write;
		document.write = function (s) {       
		document.getElementById(Configuration.tab.replace("#","")).innerHTML = s;
	}
	})();	       

	$(Configuration.tab).append(content.embedHtml.replace(/width=(auto|[0-9]+px)?/,"width=700px")
		.replace(/height=(auto|[0-9]+px)?/,"height=550px"));		  
}

$.ajax({
	url: "http://api.zoom.it/v1/content/?url=" +
	encodeURIComponent(window.location.protocol + "//" + window.location.hostname + (window.location.port ? ':' + window.location.port: '')+Configuration.url),			  
	dataType: "jsonp",
	success: onZoomitResponse
});
