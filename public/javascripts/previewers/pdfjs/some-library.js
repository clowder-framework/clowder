(function ($, Configuration) {
	console.log(Configuration);
	console.log(Configuration.previewer);
	
	// load the PDF JS library
	var s = document.createElement("script");
	s.type = "text/javascript";
	s.src = Configuration.previewer + "/pdf.js";
	$(Configuration.tab).append(s);

	$(Configuration.tab).append(
		"<button id='prevPage' value='prev'>&lt;</button>" + 
		"<button id='nextPage' value='prev'>&gt;</button><br/>" + 
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

		var mousedown = {},
			rubberbandRectangle = {},
			dragging = false,
			pageno = 1,
			page = {},
			pdf = {};

		// ----------------------------------------------------------------------
		// RUBBER BAND CODE
		// ----------------------------------------------------------------------
		function rubberbandStart(x, y) {
			mousedown.x = x;
			mousedown.y = y;

			rubberbandRectangle.left	 = mousedown.x;
			rubberbandRectangle.top		= mousedown.y;
			rubberbandRectangle.width	= 0,
			rubberbandRectangle.height = 0;

			resizeRubberbandDiv();
			moveRubberbandDiv();
			showRubberbandDiv();

			dragging = true;
		}

		function rubberbandStretch(x, y) {
			rubberbandRectangle.left	 = x < mousedown.x ? x : mousedown.x;
			rubberbandRectangle.top		= y < mousedown.y ? y : mousedown.y;
			rubberbandRectangle.width	= Math.abs(x - mousedown.x),
			rubberbandRectangle.height = Math.abs(y - mousedown.y);

			moveRubberbandDiv();
			resizeRubberbandDiv();
		}

		function rubberbandEnd() {
			var canvas = $("#rubberbandCanvas")[0];
			var rubberbandFormDiv = $("#rubberbandFormDiv")[0];
			var bbox = canvas.getBoundingClientRect();

			//rubberbandDiv.style.width = 0;
			//rubberbandDiv.style.height = 0;

			//hideRubberbandDiv();
			if ((rubberbandRectangle.width > 0) && (rubberbandRectangle.height > 0)) {
				rubberbandFormDiv.style.display = 'inline';
				rubberbandFormDiv.style.top	= (canvas.offsetTop + rubberbandRectangle.top)	+ 'px';
				rubberbandFormDiv.style.left = (canvas.offsetLeft + rubberbandRectangle.left + rubberbandRectangle.width) + 'px';
			}
			dragging = false;
		}

		function moveRubberbandDiv() {
			var canvas = $("#rubberbandCanvas")[0];
			var rubberbandDiv = $("#rubberbandDiv")[0];

			rubberbandDiv.style.top	= (canvas.offsetTop + rubberbandRectangle.top) + 'px';
			rubberbandDiv.style.left = (canvas.offsetLeft + rubberbandRectangle.left) + 'px';
		}

		function resizeRubberbandDiv() {
			var rubberbandDiv = $("#rubberbandDiv")[0];

			rubberbandDiv.style.width	= rubberbandRectangle.width + 'px';
			rubberbandDiv.style.height = rubberbandRectangle.height + 'px';
		}

		function showRubberbandDiv() {
			var rubberbandDiv = $("#rubberbandDiv")[0];
			var rubberbandFormDiv = $("#rubberbandFormDiv")[0];

			rubberbandFormDiv.style.display = 'none';
			rubberbandDiv.style.display = 'inline';
		}

		function hideRubberbandDiv() {
			var rubberbandDiv = $("#rubberbandDiv")[0];
			var rubberbandFormDiv = $("#rubberbandFormDiv")[0];

			rubberbandDiv.style.display = 'none';
			rubberbandFormDiv.style.display = 'none';
		}

		function resetRubberband() {
			var image = $("#rubberbandimage")[0];
			var canvas = $("#rubberbandCanvas")[0];
			var context = canvas.getContext('2d');
			var rubberbandDiv = $("#rubberbandDiv")[0];

			context.clearRect(0, 0, context.canvas.width, context.canvas.height);
			page.render({canvasContext: context, viewport: viewport});
			rubberbandDiv.style.width = 0;
			rubberbandDiv.style.height = 0;
			hideRubberbandDiv();

			$("#rubberbandFormTag").val("");
			$("#rubberbandFormComment").val("");
		}

		// ----------------------------------------------------------------------
		// CANVAS MOUSE EVENT HANDLERS
		// ----------------------------------------------------------------------
		$("#rubberbandCanvas").on("mousedown", function (e) {
			var x = e.offsetX;
			var y = e.offsetY;

			e.preventDefault();
			rubberbandStart(x, y);
		});

		$("#rubberbandCanvas").on("mousemove", function (e) {
			var x = e.offsetX;
			var y = e.offsetY;

			e.preventDefault();
			if (dragging) {
				rubberbandStretch(x, y);
			}
		});

		$("#rubberbandCanvas").on("mouseup", function (e) {
			e.preventDefault();
			rubberbandEnd();
		});

		// ----------------------------------------------------------------------
		// FORM SUBMISSIONS
		// ----------------------------------------------------------------------
		$("#rubberbandFormSubmit").on("click", function(e) {
			// quick check
			var tag = $("#rubberbandFormTag").val();
			var comment = $("#rubberbandFormComment").val();
			if ((tag == "") && (comment == "")) {
				resetRubberband();
				return false;
			}

			// get selected rectangle
			var canvas = $("#rubberbandCanvas")[0];
			var x = rubberbandRectangle.left / canvas.width;
			var y = rubberbandRectangle.top / canvas.height;
			var w = rubberbandRectangle.width / canvas.width;
			if (x + w > 1) {
				w = 1.0 - x;
			}
			if (w <= 0) {
				resetRubberband();
				return false;
			}
			var h = rubberbandRectangle.height / canvas.height;
			if (y + h > 1) {
				h = 1.0 - y;
			}
			if (h <= 0) {
				resetRubberband();
				return false;
			}

			// create section
			var sectionid = "";
			var request = window.jsRoutes.api.Sections.add().ajax({
				type: 		 "POST",
				contentType: "application/json",
				data:		 JSON.stringify({
								file_id: Configuration.fileid,
								area: {
									x:	    x,
									y: 	    y,
									w:	    w,
									h:	    h,
								},
							 }),
				});
			request.done(function(response, textStatus, jqXHR) {
				sectionCreated(tag, comment, response.id, x, y, w, h);
			});
			request.fail(function (jqXHR, textStatus, errorThrown){
				console.error("The following error occured: " + textStatus, errorThrown);
			});

			resetRubberband();
			return false;
		});

		// associate preview with section
		function sectionCreated(tag, comment, sectionid, x, y, w, h) {
			// clone canvas to have a subimage
			var canvas = $("#rubberbandCanvas")[0];
			var subcanvas = document.createElement("canvas");
			var cx = x * canvas.width;
			var cy = y * canvas.height;
			var cw = w * canvas.width;
			var ch = h * canvas.height;
			subcanvas.width = cw;
			subcanvas.height = ch;
			subcanvas.getContext("2d").putImageData(canvas.getContext('2d').getImageData(cx, cy, cw, ch), 0, 0);
			var imgdata = subcanvas.toDataURL("image/png");
		    var binary = atob(imgdata.split(',')[1]);
		    var array = [];
		    for(var i = 0; i < binary.length; i++) {
		        array.push(binary.charCodeAt(i));
		    }
			var data = new FormData();
			data.append("File", new Blob([new Uint8Array(array)], {type: "image/png"}), "preview.png");

			// upload image as preview
			var request = window.jsRoutes.api.Previews.upload().ajax({
                    type:        "POST",
                    data:        data,
                    contentType: false,
                    processData: false,
                });
			request.done(function(response, textStatus, jqXHR) {
				previewCreated(tag, comment, sectionid, response.id, w, h);
			});
			request.fail(function (jqXHR, textStatus, errorThrown){
				console.error("The following error occured: " + textStatus, errorThrown);
			});
		}
		
		// tag and comment on section
		function previewCreated(tag, comment, sectionid, previewid, w, h) {
			var request = window.jsRoutes.api.Previews.uploadMetadata(previewid).ajax({
				type: 		 "POST",
				contentType: "application/json",
				data:		 JSON.stringify({
								section_id:  sectionid, 
								width:       String(w),
								height:      String(h),
							  }),
				});
			request.done(function(response, textStatus, jqXHR) {
			});
			request.fail(function (jqXHR, textStatus, errorThrown){
				console.error("The following error occured: " + textStatus, errorThrown);
			});

			// add tag to section
			if (tag != "") {
				request = window.jsRoutes.api.Sections.tag(sectionid).ajax({
					type:        "POST",
					contentType: "application/json",
					data:		 JSON.stringify({
									tag: tag, 
							  	 }),
				});
				request.done(function (response, textStatus, jqXHR){ 
					var url = window.jsRoutes.controllers.Tags.search(tag).url;
					$('#tagList').append("<li><a href='" + url + "'>" + tag + "</a></li>");
					$('#tagField').val("");
				});
				request.fail(function (jqXHR, textStatus, errorThrown){
					console.error("The following error occured: " + textStatus, errorThrown);
				});
			}

			// add comment to section
			if (comment != "") {
				request = window.jsRoutes.api.Sections.comment(sectionid).ajax({
					type:        "POST",
					contentType: "application/json",
					data:		 JSON.stringify({
									comment: comment, 
							  	 }),
				});
				request.done(function (response, textStatus, jqXHR){ 
				});
				request.fail(function (jqXHR, textStatus, errorThrown){
					console.error("The following error occured: " + textStatus, errorThrown);
				});
				$("#rubberbandFormComment").val("");
			}
		}


		$("#rubberbandFormCancel").on("click", function(e) {
			$("#rubberbandFormTag").val("");
			$("#rubberbandFormComment").val("");
			resetRubberband();
			return false;
		});
	}

	// ----------------------------------------------------------------------
	// LOAD THE PDF
	// ----------------------------------------------------------------------
	PDFJS.disableWorker = true;
	PDFJS.getDocument(Configuration.url).then(function (x) {
		pageno = 1;
		pdf = x;
		console.log(pdf);
		showPage();
	});

	$("#prevPage").on("click", function(e) {
		if (pageno > 1) {
			pageno--;
			showPage();
		}
	});

	$("#nextPage").on("click", function(e) {
		if (pageno < pdf.numPages) {
			pageno++;
			showPage();
		}
	});

	function showPage() {
		pdf.getPage(pageno).then(function (y) {
			page = y;
			var canvas = $("#rubberbandCanvas")[0];
			var context = canvas.getContext('2d');

			viewport = page.getViewport(1);
			if (viewport.width > 750) {
				viewport = page.getViewport(750 / viewport.width);
			}
			canvas.width = viewport.width;
			canvas.height = viewport.height;

			// Prepare canvas using PDF page dimensions
			var context = canvas.getContext('2d');
			canvas.height = viewport.height;
			canvas.width = viewport.width;

			// Render PDF page into canvas context
			page.render({canvasContext: context, viewport: viewport});
		});
	}


}(jQuery, Configuration));
