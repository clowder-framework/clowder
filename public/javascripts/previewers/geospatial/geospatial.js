(function($, Configuration) {
	console.log("geospatial data previewer for " + Configuration.id);
	// check the metadata

	var metadataApiUrl = "/api/files/" + Configuration.fileid
			+ "/technicalmetadatajson";
	var request = $.ajax({
		type : "GET",
		url : metadataApiUrl,
		dataType : "json"
	});

	request
			.done(function(data) {
				if (data == null)
					return;
				if (data == undefined)
					return;
				if (data.length == 0)
					return;
				if (data[0] == undefined)
					return;
				if (data[0]["WMS Layer URL"] == "")
					return;

				console.log("Updating tab " + Configuration.tab);

				var cssLink = $("<link rel='stylesheet' type='text/css' href='http://openlayers.org/en/v3.0.0/css/ol.css'>");
				$(Configuration.tab).append(cssLink);

				$(Configuration.tab).append(
						"<div id='map' style='height:400px;width:100%'></div>");

				$
						.getScript(
								"http://openlayers.org/en/v3.0.0/build/ol.js",
								function() {
									console.log("ol3js loaded");

									wmsUrl = data[0]["WMS Service URL"];
									layerName = data[0]["WMS Layer Name"];
									wmsLayerUrl = data[0]["WMS Layer URL"];

									var parser = document.createElement('a'), searchObject = {}, queries, split;
									parser.href = wmsLayerUrl;
									var len = parser.search.length;
									var tmp = parser.search.substring(1,len);
									var queries = tmp.split('&');

									var ext = '';
									for (var i = 0; i < queries.length; i++) {
										split = queries[i].split('=');
										if(split[0] === 'bbox') {
											ext = split[1];
											break;
										}
									}

									var coord = ext.split(",");
									for(var i = 0; i < coord.length; i++) {
										coord[i] = parseFloat(coord[i]);
									}

									var baseLayer = new ol.layer.Tile({
										source : new ol.source.MapQuest({
											layer : 'sat'
										})
									});
									var wmsLayer = new ol.layer.Tile({
										source : new ol.source.TileWMS(({
											url : wmsUrl,
											params : {
												'LAYERS' : layerName,
												'TILED' : true
											},
											serverType : 'geoserver'
										}))
									});
									var map = new ol.Map({
										target : 'map'
									});

									map.addLayer(baseLayer);
									map.addLayer(wmsLayer);

									map.addControl(new ol.control.Zoom());
									
									console.log();
									console.log($('#map').width());
									
									zheight = $('#map').height() - 100;
									zwidth = $('#map').width();
									
									console.log('extent:',coord);
									console.log('zoom with h:'+zheight+', w:'+zwidth);
									var view = new ol.View();
									view.fitExtent(coord, [ zheight, zwidth]);

									map.setView(view);
								});

				// var s = document.createElement("script");
				// s.type = "text/javascript";
				// s.src = "http://openlayers.org/en/v3.0.0/build/ol.js";
				// s.onload = function() {
				// console.log("ol3 has been loaded");
				// var baseLayer = new ol.layer.Tile({
				// source : new ol.source.MapQuest({
				// layer : 'sat'
				// })
				// });
				// var wmsLayer = new ol.layer.Tile({
				// source : new ol.source.TileWMS(({
				// url : 'http://wssi.ncsa.illinois.edu:8080/geoserver/wms',
				// params : {
				// 'LAYERS' : 'wssi:gltg-pools-nad83',
				// 'TILED' : true
				// },
				// serverType : 'geoserver'
				// }))
				// });
				// var map = new ol.Map({
				// target : 'map'
				// });
				//
				// map.addLayer(baseLayer);
				// map.addLayer(wmsLayer);
				//
				// var view = new ol.View();
				// view.fitExtent([ -10384706.8194, 3366743.842, -9899783.13525,
				// 5631078.92574 ], [ 400, 200 ]);
				//
				// map.setView(view);
				// }
				// //$(Configuration.tab).append(s);
				// console.log("append ol3 js");
				//
				// $("head").append(s);

			});

	/*
	 * var s = document.createElement("script"); s.type = "text/javascript";
	 * s.src = "http://openlayers.org/en/v3.0.0/build/ol.js";
	 * 
	 * console.log("Updating tab " + Configuration.tab);
	 * $(Configuration.tab).append(s); $(Configuration.tab).append( "<video
	 * width='600px' id='ourvideo' controls><source src='" + Configuration.url +
	 * "'></source></video>" );
	 */
	// var height = 400;
	// var width = 750;
	//  
	// $(Configuration.tab).append("<p>For panoramic images viewed through
	// Quicktime, Shift can be used to zoom in and Ctrl to zoom out.</p>");
	//    
	// console.log("Updating tab " + Configuration.tab);
	// $(Configuration.tab).append(s);
	// $(Configuration.tab).append(
	// "<video width='600px' id='ourvideo' controls><source src='" +
	// Configuration.url + "'></source></video>"
	// );
}(jQuery, Configuration));