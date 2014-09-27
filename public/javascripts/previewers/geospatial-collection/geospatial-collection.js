(function($, Configuration) {
	console.log("geospatial collection previewer for " + Configuration.collection_id);

    console.log("Updating tab " + Configuration.tab);

    var defaultOpacity = 0.5;
    
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
			
			// adding div for layer control
			var layerControlDiv = "<div id='toolbox' style='position:absolute; top:45px; right:20px; padding:3px; border-radius:4px; color:#fff; background: rgba(255, 255, 255, 0.4); z-index:100;' >";
			layerControlDiv += "<div id='control-title' style='color:black; cursor:pointer;'><span class='glyphicon glyphicon-tasks'></span>&nbsp;Geospatial Layers</div>";
			layerControlDiv += "<div id='layer-control' style='margin:0; padding:10px; border-radius:4px; background:rgba(0, 60, 136, 0.5);'></div></div>";
			$(Configuration.tab).append(layerControlDiv);

			// adding event handler for collapsible div
			$('#control-title').click(function(){
	            $('#layer-control').toggle();
	        });
			
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
					
					// this variable will be placed in $.ajax call to be used in .done function
					var dataset_title = data[i]['datasetname'];
					
					// setting up ajax call to get file from the dataset
					var file_req = $.ajax({
						datasetTitle: dataset_title,
						type: "GET",
						url: "/api/datasets/"+dataset_id+"/listFiles",
						dataType: "json"
					});
					
					file_req.done(function(data){
						// this variable will be placed in $.ajax call to be used in .done function
						var title = this.datasetTitle;
						
						var file_id = data[0]['id'];
						
						// setting up ajax call to get metadata from the file
						var meta_req = $.ajax({
							datasetTitle: title,
							type: "GET",
							url: "/api/files/"+file_id+"/technicalmetadatajson",
							dataType: "json"
						});
						meta_req.done(function(data){
							// this variable will be placed in $.ajax call to be used in .done function
							var title = this.datasetTitle;
							
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
							
							// remove the 'wssi:' (workspace prefix) 
							var name = layerName.split(":")
							
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
								})),
								opacity: defaultOpacity
							});
							
							// add wms layer to the map
							map.addLayer(wmsLayer);
							
							// Populate layer control for each layer
							
							// create id by using wms layer name
							var visVar = name[1]+'-visible'; 
							var opVar = name[1]+'-opacity';
							
							// add checkbox and range input
							var layerControl = '<div><input id="'+visVar+'" type="checkbox" checked="checked" />'+title+':&nbsp;&nbsp';
							layerControl += '<input id="'+opVar+'" type="range" min="0" max="1" step="0.01" value="'+defaultOpacity+'" style="width:100px;"/></div>';
							
							// prepend the layer not "append" since the top item means the layer on top
							$('#layer-control').prepend(layerControl);
							
							// event handler for layer on/off
							$("#"+visVar).change(function() {
								if($(this).is(':checked')){
									wmsLayer.setVisible(true);
								} else {	
									wmsLayer.setVisible(false);
								}
							});
							
							// event handler for layer opacity
							$("#"+opVar).change(function() {
								wmsLayer.setOpacity($(this).val());
							});
							
							
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