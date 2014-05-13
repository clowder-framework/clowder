(function ($, Configuration) {
	console.log("Gigaimage previewer (zoom.it) for " + Configuration.id);
	
	console.log("Updating tab " + Configuration.tab);
	
	var pathJs = "http://" + Configuration.hostIp + ":" + window.location.port + Configuration.jsPath + "/";
	
	var width = 750;
	var height = 550;
		
	var s = document.createElement("script");
	s.type = "text/javascript";
	s.src = pathJs + "zoomit.js";
	console.log("Updating tab " + Configuration.tab);
	$(Configuration.tab).append(s);

	 $(Configuration.tab).append(
		     "<div style='width: " + width + "px; height: " + height + "px' id='seadragon" + Configuration.tab.replace("#previewer","") + "'>Insert image here</div>"
		  );

	 viewer = new Seadragon.Viewer("seadragon" + Configuration.tab.replace("#previewer",""));
	 viewer.openDzi(Configuration.url);
	 			
}(jQuery, Configuration));