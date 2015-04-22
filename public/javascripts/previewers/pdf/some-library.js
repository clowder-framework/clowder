(function ($, Configuration) {
	console.log(Configuration);
	console.log(Configuration.previewer);
	
	var prNum = Configuration.tab.replace("#previewer","");
	window["configsFileId" + prNum] = Configuration.fileid;
	
	// load the PDF JS library
	var s = document.createElement("script");
	s.type = "text/javascript";
	s.src = Configuration.previewer + "/pdf.js";
	$(Configuration.tab).append(s);

	$(Configuration.tab).append(
		"<button id='prevPage"+prNum+"' value='prev'>&lt;</button>" + 
		"<button id='nextPage"+prNum+"' value='prev'>&gt;</button><br/>" +
		"<canvas class='fit-in-space rubberbandCanvas' id='rubberbandCanvas"+prNum+"'/>" +
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
	// LOAD THE PDF
	// ----------------------------------------------------------------------
	PDFJS.disableWorker = true;
	PDFJS.getDocument(Configuration.url).then(function (x) {
		window["pageno" + prNum] = 1;
		window["pdf" + prNum] = x;
		console.log(window["pdf" + prNum]);
		showPage(prNum);
	});

	$("#prevPage"+prNum).on("click", function(e) {
		if (window["pageno" + prNum] > 1) {
			window["pageno" + prNum]--;
			showPage(prNum);
		}
	});

	$("#nextPage"+prNum).on("click", function(e) {
		if (window["pageno" + prNum] < window["pdf" + prNum].numPages) {
			window["pageno" + prNum]++;
			showPage(prNum);
		}
	});

	function showPage(prNum) {
		window["pdf" + prNum].getPage(window["pageno" + prNum]).then(function (y) {
			window["page" + prNum] = y;
			var canvas = $("#rubberbandCanvas"+prNum)[0];
			var context = canvas.getContext('2d');

			viewport = window["page" + prNum].getViewport(1);
			if (viewport.width > 750) {
				viewport = window["page" + prNum].getViewport(750 / viewport.width);
			}
			canvas.width = viewport.width;
			canvas.height = viewport.height;

			// Prepare canvas using PDF page dimensions
			var context = canvas.getContext('2d');
			canvas.height = viewport.height;
			canvas.width = viewport.width;

			// Render PDF page into canvas context
			window["page" + prNum].render({canvasContext: context, viewport: viewport});
		});
	}

	$(Configuration.tab).append("<br/><p>Note: 3D models embedded in PDF cannot be interacted with using this previewer. You can use the \"Pdf\"" +
								" previewer to interact with them (use Firefox if on Unix) or download the PDF and view the model on your desktop.</p>");


}(jQuery, Configuration));
