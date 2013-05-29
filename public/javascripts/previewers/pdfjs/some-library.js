(function ($, Configuration) {
	console.log(Configuration);
	console.log(Configuration.previewer);
	
	// load the PDF JS library
	var s = document.createElement("script");
	s.type = "text/javascript";
	s.src = Configuration.previewer + "/pdf.js";
	$(Configuration.tab).append(s);

	$(Configuration.tab).append(
		"<canvas id='rubberbandCanvas'/>" +
		"<div id='rubberbandDiv'></div>"
		);

  	if (Configuration.authenticated) {
  		$("#rubberbandCanvas").css("cursor", "crosshair");

		$(Configuration.tab).append(
			"<div id='rubberbandFormDiv'><form id='rubberbandForm' action='#' onsubmit='return false;'>" +
			"<fieldset>" +
			"<label for='rubberbandFormTag'>Tag :</label><input type='text' id='rubberbandFormTag' />" +
			"<label for='rubberbandFormComment'>Comment :</label><textarea type='text' id='rubberbandFormComment'></textarea>" +
			"</fieldset>" +
			"<input type='button' id='rubberbandFormSubmit' value='Submit' />" +
			"<input type='button' id='rubberbandFormCancel' value='Cancel' />" +
			"</form></div>"
		);
  	}

	var mousedown = {},
		rubberbandRectangle = {},
		dragging = false;

	// ----------------------------------------------------------------------
	// LOAD THE PDF
	// ----------------------------------------------------------------------
	PDFJS.disableWorker = true;
	PDFJS.getDocument(Configuration.url).then(function getPdfHelloWorld(pdf) {
		pdf.getPage(1).then(function getPageHelloWorld(page) {
			var scale = 1;
			var viewport = page.getViewport(scale);

			// Prepare canvas using PDF page dimensions
			var canvas = $("#rubberbandCanvas")[0];
			var context = canvas.getContext('2d');
			canvas.height = viewport.height;
			canvas.width = viewport.width;

			// Render PDF page into canvas context
			page.render({canvasContext: context, viewport: viewport});
		});
	});

}(jQuery, Configuration));
