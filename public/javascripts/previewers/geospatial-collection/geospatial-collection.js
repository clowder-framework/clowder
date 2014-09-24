(function($, Configuration) {
	console.log("geospatial collection previewer for " + Configuration.collection_id);

    console.log("Updating tab " + Configuration.tab);

    var col_id = Configuration.collection_id;
    
    // setting up ajax call to get list of datasets from the collections
	var request = $.ajax({
		type: "GET",
		url: "/api/collections/"+col_id+"/getDatasets",
		dataType: "json"
	});
	
	request.done(function(data) {
		console.log(data.length+" datasets");
		if(data.length > 0) {
			// adding css for ol3
			var cssLink = $("<link rel='stylesheet' type='text/css' href='http://openlayers.org/en/v3.0.0/css/ol.css'>");
			$(Configuration.tab).append(cssLink);
			
			// adding map div for rendering the map
			var mapDiv = "<div id='map' style='height:400px;width:100%'></div>"
			$(Configuration.tab).append(mapDiv);
			
			// loading the ol3 script
			$.getScript("http://openlayers.org/en/v3.0.0/build/ol.js", function() {
				// initiating map 
				var map = new ol.Map({
					target: 'map'
				});
				
				// setting up base layer with OSM
				var baseLayer = new ol.layer.Tile({
					source : new ol.source.OSM()
				});
				
				// adding the base layer to the map
				map.addLayer(baseLayer);
				
				var current_coord = [];
				// looping through the datasets
				for (var i=0;i < data.length; i ++) {
					var dataset_id = data[i]['id'];
					
					// setting up ajax call to get file from the dataset
					var file_req = $.ajax({
						type: "GET",
						url: "/api/datasets/"+dataset_id+"/listFiles",
						dataType: "json"
					});
					
					file_req.done(function(data){
						var file_id = data[0]['id'];
						
						// setting up ajax call to get metadata from the file
						var meta_req = $.ajax({
							type: "GET",
							url: "/api/files/"+file_id+"/technicalmetadatajson",
							dataType: "json"
						});
						meta_req.done(function(data){
							// skip files without wms metadata
							if (data == null) {
								console.log("NO - null");
								return;
							}
							if (data == undefined) {
								console.log("NO - undefined");
								return;
							}
							if (data.length == undefined) {
								console.log("NO - length undefined");
								return;
							}
							if (data[0]["WMS Layer URL"] == "") {
								console.log("NO - no wms metadata"); 
								return;
							}
							console.log("YES - it is a geospatial data");
							
							// retrieve wms information 
							var wmsUrl = data[0]["WMS Service URL"];
							var layerName = data[0]["WMS Layer Name"];
							var wmsLayerUrl = data[0]["WMS Layer URL"];

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
							
							// update the extent for all the layers
							if (current_coord.length == 0) {
								for (var i = 0; i < coord.length; i++) {
									current_coord[i] = coord[i];
								}
							} else {
								// min x
								if (coord[0] < current_coord[0]) current_coord[0] = coord[0];
								
								// min y
								if (coord[1] < current_coord[1]) current_coord[1] = coord[1];
								
								// max x
								if (coord[2] > current_coord[2]) current_coord[2] = coord[2];
								
								// max y
								if (coord[3] > current_coord[3]) current_coord[3] = coord[3];
							}
							
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
							
							// add wms layer to the map
							map.addLayer(wmsLayer);
							
							// retrieving the map div size for fit
//							zheight = $('#map').height() - 100;
//							zwidth = $('#map').width();
//
//							console.log('extent:', current_coord);
//							console.log('zoom with h:' + zheight
//									+ ', w:' + zwidth);

							// create view object to zoom in
							var view = new ol.View();
							view.fitExtent(current_coord, map.getSize());

							// zoom into the layer extent
							map.setView(view);
						});
					});
				}
				// fix for MMDB-1617
				// force to redraw the map
				// TODO the dom selector needs to select the current selector instead of this selection 
				$('a[data-toggle="tab"]').on('shown.bs.tab', function(e) {
					map.updateSize()
				});
			});
		}
	});
}(jQuery, Configuration));