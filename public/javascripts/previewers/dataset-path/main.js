(function($, Configuration) {

	function countRoutes(data) {
		if ("Route and Green Index Extractor" in data) {
			return Object.keys(data["Route and Green Index Extractor"]["Data"]["Routes"]).length;
		} else {
			return 0;
		}
	}
    
    var dataset_id = Configuration.dataset_id;

	// setting up ajax call to get metadata from the file
	var req = $.ajax({
		type: "GET",
		url: jsRoutes.api.Datasets.getTechnicalMetadataJSON(dataset_id).url,
		dataType: "json"
	});

	req.done(function(data){

		var numRoutes = countRoutes(data);

		if (numRoutes > 0) {

			console.log("Dataset path previewer found " + numRoutes + " routes")

			//$.getScript("https://maps.googleapis.com/maps/api/js?key=AIzaSyBJHzizWxzgqkH9Ipr_cGSjCWpeuY8M4ac", function() {
			//$.getScript("https://maps.googleapis.com/maps/api/js?v=3.exp&signed_in=true&callback=initialize", function() {

			function initialize() {

				// add css
				$("<link/>", {
					rel: "stylesheet",
					type: "text/css",
					href: Configuration.path + "main.css"
				}).appendTo(Configuration.div);

				var colors = ["#FFB300", "#803E75", "#FF6800", "#A6BDD7", "#C10020", "#CEA262", "#817066"];

				$(Configuration.div).append("<h4>Paths</h4><div id='ds-path-map-canvas'></div>");

				var mapOptions = {
					center: {lat: -34.397, lng: 150.644},
					zoom: 8
				};

				var map = new google.maps.Map(document.getElementById('ds-path-map-canvas'),
					mapOptions);
				var bounds = new google.maps.LatLngBounds();

				for (var routeNum = 1; routeNum <= numRoutes; routeNum++) {

					var route = JSON.parse(data["Route and Green Index Extractor"]["Data"]["Routes"]["Route" + routeNum]["Path"]);

					var images = data["Route and Green Index Extractor"]["Data"]["Routes"]["Route" + routeNum]["Images"];

					if (route != null) {
						var routeCoordinates = [];
						var infowindow = infowindow = new google.maps.InfoWindow({
							content: "initializing..."
						});
						for (var x in route) {
							routeCoordinates.push(new google.maps.LatLng(+route[x][0], +route[x][1]));

							var content = '';
							for (var y in images[x]) {
								if (y % 2 == 0) content = content.concat('<div class="row">');
								content = content.concat('<div class="col-md-6"><a href="' + images[x][y] + '"><img class="img-thumbnail ds-path-img-tmb" src="' + images[x][y] + '/blob"></img></a></div>');
								if (y % 2 == 1) content = content.concat('</div>');
							}

							var marker = new google.maps.Marker({
								position: new google.maps.LatLng(+route[x][0], +route[x][1]),
								map: map,
								title: 'Location',
								popup_content: content
							});

							google.maps.event.addListener(marker, 'click', function () {
								infowindow.setContent(this.popup_content);
								infowindow.open(map, this);
							});
						}

						var color = colors[colors.length % routeNum];

						var routePath = new google.maps.Polyline({
							path: routeCoordinates,
							geodesic: true,
							strokeColor: color,
							strokeOpacity: 1.0,
							strokeWeight: 4
						});

						routePath.setMap(map);

						for (var i = 0; i < routeCoordinates.length; i++) {
							bounds.extend(routeCoordinates[i]);
						}
					}
				}

				map.fitBounds(bounds);

				$('a[href$="#tab-home"]').on('shown.bs.tab', function (e) {
					map.fitBounds(bounds);
					google.maps.event.trigger(map, 'resize');
				});

			}
			google.maps.event.addDomListener(window, 'load', initialize);
		}
	});
}(jQuery, Configuration));