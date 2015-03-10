(function ($, Configuration) {
	console.log(Configuration);
	console.log(Configuration.previewer);

	var prNum = Configuration.tab.replace("#previewer","");
	window["configs" + prNum] = Configuration;
		
	// --------------------------------------------------------
	// IMAGE FORMATS SUPPORTED BY MOST BROWSERS
	// --------------------------------------------------------
	if(Configuration.fileType === "image/jpeg" || Configuration.fileType === "image/jpg" || Configuration.fileType === "image/png" || Configuration.fileType === "image/gif"
		|| Configuration.fileType === "image/bmp"){
		$(Configuration.tab).append(
			"<canvas class='fit-in-space rubberbandCanvas' id='rubberbandCanvas"+prNum+"'>" +
			"<img src='" + Configuration.url + "' class='rubberbandimage' id='rubberbandimage"+prNum+"'></img>" +
			"</canvas>" +
			"<div class='rubberbandDiv' id='rubberbandDiv"+prNum+"'></div>"
		);

		if (Configuration.authenticated) {
			// load the rubberband library
			var s = document.createElement("script");
			s.type = "text/javascript";
			s.src = Configuration.previewer + "/../sectionRubberband.js";
			$(Configuration.tab).append(s);

			$(Configuration.tab).append(sectionRubberband(prNum));
		}

		// ----------------------------------------------------------------------
		// IMAGE LOADED CODE
		// ----------------------------------------------------------------------
		$("#rubberbandimage"+prNum).on("load", function() {
			var image = $("#rubberbandimage"+prNum)[0];
			var canvas = $("#rubberbandCanvas"+prNum)[0];
			var context = canvas.getContext('2d');

			if (image.width < 750) {
				canvas.width = image.width;
				canvas.height = image.height;
			} else {
				canvas.width = 750;
				canvas.height = image.height * (canvas.width / image.width);
			}

			context.drawImage(image, 0, 0, canvas.width, canvas.height);
		});
	}

	// --------------------------------------------------------
	// TIFF IMAGES
	// --------------------------------------------------------
	else if (Configuration.fileType === "image/tiff"){
		$(Configuration.tab).append(
			"<embed alt='No plugin capable of displaying TIFF images was found.' width=750 height=550	"+
				"src='" + Configuration.url + "' type='image/tiff'"+
				" negative=no>"
			);
				
	}

	// --------------------------------------------------------
	// UNKNOWN IMAGE FORMAT
	// --------------------------------------------------------
	else{
		$(Configuration.tab).append(
			"<b>ERROR: Unrecognised image format.</b>"
	 	);
	}
		
}(jQuery, Configuration));
