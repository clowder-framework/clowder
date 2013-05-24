$(Configuration.tab).append(
	"<canvas id='rubberbandCanvas'>" +
	"<img src='" + Configuration.url + "' id='rubberbandimage'></img>" +
	"</canvas>");

$("#rubberbandimage").on("load", function() {
	var image = $("#rubberbandimage")[0];
	var canvas = $("#rubberbandCanvas")[0];
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

