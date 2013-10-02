(function ($, Configuration) {
	console.log("Gigaimage previewer (IIP) for " + Configuration.id);
	
	console.log("Updating tab " + Configuration.tab);
	
	var width = 750;
	var height = 550;

//	var s = document.createElement("script");
//	s.type = "text/javascript";
//	s.src = "http://ajax.googleapis.com/ajax/libs/swfobject/2.2/swfobject.js";
//	console.log("Updating tab " + Configuration.tab);
//	$(Configuration.tab).append(s);
	$(Configuration.tab).append("<p>Right-click on image for help navigating.</p>");
	
	$(Configuration.tab).append(
		     "<div class='iipDiv' style='width: " + width + "px; height: " + height + "px' id='iip" + Configuration.tab.replace("#previewer","") + "'>Your browser does not support Flash.</div>"
		  );
	
	  $.ajax({
		    url: Configuration.url,
		    async:false,
		    success: function (data) {
		    	var uploadDirs = data.split("\n");
		    	
		    	var server = uploadDirs[0].substring(8);
		    	var image = uploadDirs[1].substring(7);
		    	var flashvars = {
		    		server: server,
		    		image: image,
		    		navigation: true
		    	};
		    	var params = {
		    		scale: "noscale",
		    		bgcolor: "#000000",
		    		allowfullscreen: "true",
		    		allowscriptaccess: "always"
		    	};
		    	swfobject.embedSWF(Configuration.iipZoomPath, "iip" + Configuration.tab.replace("#previewer",""), width, height, "9.0.0",Configuration.expressInstallPath, flashvars, params);
		    	 },
		    dataType: 'text'
		});

	 			
}(jQuery, Configuration));