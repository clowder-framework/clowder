(function($, Configuration) {
	console.log("geospatial data previewer for " + Configuration.id);

	// retrieve the metadata
	var metadataApiUrl = "/api/files/" + Configuration.fileid
			+ "/technicalmetadatajson";
	var request = $.ajax({
		type : "GET",
		url : metadataApiUrl,
		dataType : "json"
	});

	request
			.done(function(data) {

				// if there are no tehcnical metadata or no wms metadata
				// then return
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

				// add css for ol3
				var cssLink = $("<link rel='stylesheet' type='text/css' href='http://openlayers.org/en/v3.0.0/css/ol.css'>");
				$(Configuration.tab).append(cssLink);

				// add map div for ol3
				$(Configuration.tab).append(
						"<div id='map' style='height:400px;width:100%'></div>");

				// loading the ol3 javascript
				$
						.getScript(
								"http://openlayers.org/en/v3.0.0/build/ol.js",
								function() {
									// drawing the map
									console.log("ol3js loaded");

									wmsUrl = data[0]["WMS Service URL"];
									layerName = data[0]["WMS Layer Name"];
									wmsLayerUrl = data[0]["WMS Layer URL"];

									// extract extent from WMS layer url
									var parser = document.createElement('a'), searchObject = {}, queries, split;
									parser.href = wmsLayerUrl;
									var tmp = parser.search.substring(1,
											parser.search.length);
									var queries = tmp.split('&');

									var ext = '';
									for (var i = 0; i < queries.length; i++) {
										split = queries[i].split('=');
										if (split[0] === 'bbox') {
											ext = split[1];
											break;
										}
									}
									var coord = ext.split(",");
									for (var i = 0; i < coord.length; i++) {
										coord[i] = parseFloat(coord[i]);
									}

									// create base layer by using OSM
									// TODO: create a dropdown box for different backdrop
									var baseLayer = new ol.layer.Tile({
										source : new ol.source.OSM()
									});

									// create wms layer
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

									// create map object
									var map = new ol.Map({
										target : 'map'
									});

									// add layers
									map.addLayer(baseLayer);
									map.addLayer(wmsLayer);

									// retrieving the map div size for fit
									zheight = $('#map').height() - 100;
									zwidth = $('#map').width();

									console.log('extent:', coord);
									console.log('zoom with h:' + zheight
											+ ', w:' + zwidth);

									// create view object to zoom in
									var view = new ol.View();
									view.fitExtent(coord, [ zheight, zwidth ]);

									// zoom into the layer extent
									map.setView(view);
									
									// fix for MMDB-1617
									// force to redraw the map
									// TODO the dom selector needs to select the current selector instead of this selection 
									$('a[data-toggle="tab"]').on(
											'shown.bs.tab', function(e) {
												map.updateSize()
											})
								});

			});

}(jQuery, Configuration));