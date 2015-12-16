(function($, Configuration) {
	console.log("geospatial data previewer for " + Configuration.id);

	var defaultOpacity = 0.8;
	// retrieve the metadata
	var metadataApiUrl = jsRoutes.api.Files.getTechnicalMetadataJSON(Configuration.fileid).url;
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

                // search the metadata index which contains geospatial metadata
                var geoMetadataIndex = -1;
                for (var i=0;i<data.length;i++)
                {
                    if (data[i]["WMS Layer URL"] == undefined)
                        continue;
                    if (data[i]["WMS Layer URL"] == "")
                        continue;
                    geoMetadataIndex = i;
                    break;
                }

                // if it couldn't find the index, return
                if (geoMetadataIndex == -1)
                    return;

				console.log("Updating tab " + Configuration.tab);

				// add css for ol3
				var cssLink = $("<link rel='stylesheet' type='text/css' href='" + Configuration.previewer + "/../../openlayers/ol.css'>");
				$(Configuration.tab).append(cssLink);

				// add map div for ol3
				$(Configuration.tab).append(
						"<div id='map' class='fit-in-space' style='height:400px;width:100%'></div>");
				// loading the ol3 javascript
				$
						.getScript(
								Configuration.previewer + "/../../openlayers/ol.js",
								function() {
									// add layer opacity control
									$(Configuration.tab).append(
											"<div id='layer-opacity-control'><label>Layer Opacity: </label><input id='opacity' type='range' min='0' max='1' value='"+defaultOpacity+"' step='0.01' style='width:200px;' /></div>");
									
									// drawing the map
									console.log("ol3js loaded");

									wmsUrl = data[geoMetadataIndex]["WMS Service URL"];
									layerName = data[geoMetadataIndex]["WMS Layer Name"];
									wmsLayerUrl = data[geoMetadataIndex]["WMS Layer URL"];

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
										})),
										opacity: defaultOpacity
									});

									$('#opacity').change( function(e) {
										wmsLayer.setOpacity($(this).val());
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
									});
								});

			});

}(jQuery, Configuration));
