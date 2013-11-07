

(function ($, Configuration) {	
	var prNum = Configuration.tab.replace("#previewer","");
	window["configs" + prNum] = Configuration;
		
	// --------------------------------------------------------
	// IMAGE FORMATS SUPPORTED BY MOST BROWSERS
	// --------------------------------------------------------
	if(Configuration.fileType === "image/jpeg" || Configuration.fileType === "image/jpg" || Configuration.fileType === "image/png" || Configuration.fileType === "image/gif"
		|| Configuration.fileType === "image/bmp"){
		$(Configuration.tab).append(
			"<canvas class='rubberbandCanvas' id='rubberbandCanvas"+prNum+"'>" +
			"<img src='" + Configuration.url + "' class='rubberbandimage' id='rubberbandimage"+prNum+"'></img>" +
			"</canvas>" +
			"<div class='rubberbandDiv' id='rubberbandDiv"+prNum+"'></div>"
		);

		if (Configuration.authenticated) {
			$("#rubberbandCanvas"+prNum).css("cursor", "crosshair");

			$(Configuration.tab).append(
				"<div class='rubberbandFormDiv' id='rubberbandFormDiv"+prNum+"'><form id='rubberbandForm' id='rubberbandForm"+prNum+"' action='#' onsubmit='return false;'>" +
				"<fieldset>" +
				"<label for='rubberbandFormTag"+prNum+"'>Tag :</label><input type='text' id='rubberbandFormTag"+prNum+"' />" +
				"<label for='rubberbandFormComment"+prNum+"'>Comment :</label><textarea type='text' id='rubberbandFormComment"+prNum+"'></textarea>" +
				"</fieldset>" +
				"<input type='button' id='rubberbandFormSubmit"+prNum+"' value='Submit' />" +
				"<input type='button' id='rubberbandFormCancel"+prNum+"' value='Cancel' />" +
				"</form></div>"
			);

			var mousedown = {},
				rubberbandRectangle = {},
				dragging = false;

			// ----------------------------------------------------------------------
			// RUBBER BAND CODE
			// ----------------------------------------------------------------------
			function rubberbandStart(x, y, prNum) {
				mousedown.x = x;
				mousedown.y = y;

				rubberbandRectangle.left	 = mousedown.x;
				rubberbandRectangle.top		= mousedown.y;
				rubberbandRectangle.width	= 0,
				rubberbandRectangle.height = 0;

				resizeRubberbandDiv(prNum);
				moveRubberbandDiv(prNum);
				showRubberbandDiv(prNum);

				dragging = true;
			}

			function rubberbandStretch(x, y, prNum) {
				rubberbandRectangle.left   = x < mousedown.x ? x : mousedown.x;
				rubberbandRectangle.top    = y < mousedown.y ? y : mousedown.y;
				rubberbandRectangle.width  = Math.abs(x - mousedown.x),
				rubberbandRectangle.height = Math.abs(y - mousedown.y);

				moveRubberbandDiv(prNum);
				resizeRubberbandDiv(prNum);
			}

			function rubberbandEnd(prNum) {
				var canvas = $("#rubberbandCanvas"+prNum)[0];
				var rubberbandFormDiv = $("#rubberbandFormDiv"+prNum)[0];
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

			function moveRubberbandDiv(prNum) {
				var canvas = $("#rubberbandCanvas"+prNum)[0];
				var rubberbandDiv = $("#rubberbandDiv"+prNum)[0];

				rubberbandDiv.style.top	= (canvas.offsetTop + rubberbandRectangle.top) + 'px';
				rubberbandDiv.style.left = (canvas.offsetLeft + rubberbandRectangle.left) + 'px';
			}

			function resizeRubberbandDiv(prNum) {
				var rubberbandDiv = $("#rubberbandDiv"+prNum)[0];

				rubberbandDiv.style.width	= rubberbandRectangle.width + 'px';
				rubberbandDiv.style.height = rubberbandRectangle.height + 'px';
			}

			function showRubberbandDiv(prNum) {
				var rubberbandDiv = $("#rubberbandDiv"+prNum)[0];
				var rubberbandFormDiv = $("#rubberbandFormDiv"+prNum)[0];

				rubberbandFormDiv.style.display = 'none';
				rubberbandDiv.style.display = 'inline';
			}

			function hideRubberbandDiv(prNum) {
				var rubberbandDiv = $("#rubberbandDiv"+prNum)[0];
				var rubberbandFormDiv = $("#rubberbandFormDiv"+prNum)[0];

				rubberbandDiv.style.display = 'none';
				rubberbandFormDiv.style.display = 'none';
			}

			function resetRubberband(prNum) {
				var image = $("#rubberbandimage"+prNum)[0];
				var canvas = $("#rubberbandCanvas"+prNum)[0];
				var context = canvas.getContext('2d');
				var rubberbandDiv = $("#rubberbandDiv"+prNum)[0];

				context.clearRect(0, 0, context.canvas.width, context.canvas.height);
				context.drawImage(image, 0, 0, canvas.width, canvas.height);
				rubberbandDiv.style.width = 0;
				rubberbandDiv.style.height = 0;
				hideRubberbandDiv(prNum);

				$("#rubberbandFormTag"+prNum).val("");
				$("#rubberbandFormComment"+prNum).val("");
			}

			// ----------------------------------------------------------------------
			// CANVAS MOUSE EVENT HANDLERS
			// ----------------------------------------------------------------------
			$("#rubberbandCanvas"+prNum).on("mousedown", function (e) {
				var x = e.offsetX;
				var y = e.offsetY;

				e.preventDefault();
				rubberbandStart(x, y, prNum);
			});

			$("#rubberbandCanvas"+prNum).on("mousemove", function (e) {
				var x = e.offsetX;
				var y = e.offsetY;

				e.preventDefault();
				if (dragging) {
					rubberbandStretch(x, y, prNum);
				}
			});

			$("#rubberbandCanvas"+prNum).on("mouseup", function (e) {
				e.preventDefault();
				rubberbandEnd(prNum);
			});

			// ----------------------------------------------------------------------
			// FORM SUBMISSIONS
			// ----------------------------------------------------------------------
			$("#rubberbandFormCancel"+prNum).on("click", function(e) {
				$("#rubberbandFormTag"+prNum).val("");
				$("#rubberbandFormComment"+prNum).val("");
				resetRubberband(prNum);
				return false;
			});

			$("#rubberbandFormSubmit"+prNum).on("click", function(e) {
				// quick check
				var tag = $("#rubberbandFormTag"+prNum).val();
				var comment = $("#rubberbandFormComment"+prNum).val();
				if ((tag == "") && (comment == "")) {
					resetRubberband(prNum);
					return false;
				}

				// get selected rectangle
				var canvas = $("#rubberbandCanvas"+prNum)[0];
				var x = rubberbandRectangle.left / canvas.width;
				var y = rubberbandRectangle.top / canvas.height;
				var w = rubberbandRectangle.width / canvas.width;
				if (x + w > 1) {
					w = 1.0 - x;
				}
				if (w <= 0) {
					resetRubberband(prNum);
					return false;
				}
				var h = rubberbandRectangle.height / canvas.height;
				if (y + h > 1) {
					h = 1.0 - y;
				}
				if (h <= 0) {
					resetRubberband(prNum);
					return false;
				}

				// create section
				var sectionid = "";
				var request = window.jsRoutes.api.Sections.add().ajax({
					type: 		 "POST",
					contentType: "application/json",
					data:		 JSON.stringify({
									file_id: window["configs" + prNum].fileid,
									area: {
										x:	    x,
										y: 	    y,
										w:	    w,
										h:	    h,
									},
								 }),
					});
				request.done(function(response, textStatus, jqXHR) {
					sectionCreated(tag, comment, response.id, x, y, w, h, prNum);
				});
				request.fail(function (jqXHR, textStatus, errorThrown){
					console.error("The following error occured: " + textStatus, errorThrown);
				});

				resetRubberband(prNum);
				return false;
			});

			// associate preview with section
			function sectionCreated(tag, comment, sectionid, x, y, w, h, prNum) {
				// clone canvas to have a subimage
				var canvas = $("#rubberbandCanvas"+prNum)[0];
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
					previewCreated(tag, comment, sectionid, response.id, w, h, prNum);
				});
				request.fail(function (jqXHR, textStatus, errorThrown){
					console.error("The following error occured: " + textStatus, errorThrown);
				});
			}
			
			// tag and comment on section
			function previewCreated(tag, comment, sectionid, previewid, w, h, prNum) {
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
						$('#tagList'+prNum).append("<li><a href='" + url + "'>" + tag + "</a></li>");
						$('#tagField'+prNum).val("");
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
										text: comment, 
								  	 }),
					});
					request.done(function (response, textStatus, jqXHR){ 
					});
					request.fail(function (jqXHR, textStatus, errorThrown){
						console.error("The following error occured: " + textStatus, errorThrown);
					});
					$("#rubberbandFormComment"+prNum).val("");
				}
			}
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
