$(Configuration.tab).append(
	"<canvas id='rubberbandCanvas' width='750px' height='auto'>" +
	"<img src='" + Configuration.url + "' width='750px' id='rubberbandimage'></img>" +
	"</canvas>");

$("#rubberbandimage").on("load", function() {
	var image = $("#rubberbandimage")[0];
	var canvas = $("#rubberbandCanvas")[0];
	var context = canvas.getContext('2d');

	canvas.height = image.height * (canvas.width / image.width);
	context.drawImage(image, 0, 0, canvas.width, canvas.height);
});

