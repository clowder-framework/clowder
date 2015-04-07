(function($, Configuration) {
	console.log("Geospatial dataset previewer for " + Configuration.dataset_id);

	$.getScript("http://openlayers.org/en/v3.0.0/build/ol.js", function () {

		var dataset_id = Configuration.dataset_id;

		var current_coord = [];

		// setting up ajax call to get file from the dataset
		var file_req = $.ajax({
			type: "GET",
			url: "/api/datasets/" + dataset_id + "/listFiles",
			dataType: "json"
		});

		file_req.done(function (files) {

			console.log(files);

			for (var i in files) {

				var fileId = files[i]['id'];

				var fileName = files[i]['filename'];
				if (fileName.length > 15) {
					fileName = fileName.substring(0, 15) + "...";
				}

				// setting up ajax call to get metadata from the file
				var meta_req = $.ajax({
					fileName: fileName,
					fileId: fileId,
					type: "GET",
					url: "/api/files/" + fileId + "/technicalmetadatajson",
					dataType: "json"
				});

				meta_req.done(function (metadata) {
					console.log("file metadata");
					console.log(metadata);
                    // skip files without wms metadata
                    if (metadata == null) {
                        console.log("NO - null");
                        return;
                    }
                    if (metadata == undefined) {
                        console.log("NO - undefined");
                        return;
                    }
                    if (metadata.length == undefined) {
                        console.log("NO - length undefined");
                        return;
                    }

                    // search the metadata index which contains geospatial metadata
                    var geoMetadataIndex = -1;
                    for (var i=0;i<metadata.length;i++)
                    {
                        if (metadata[i]["WMS Layer URL"] == undefined) {
                            console.log("NO - wms metadata is empty");
                            continue;
                        }
                        if (metadata[i]["WMS Layer URL"] == "") {
                            console.log("NO - no wms metadata");
                            continue;
                        }
                        geoMetadataIndex = i;
                        break;
                    }

                    // if it couldn't find the index, return
                    if (geoMetadataIndex == -1)
                        return;

                    console.log("YES - it is a geospatial data");

                    var wmsUrl = metadata[geoMetadataIndex]["WMS Service URL"];
                    var layerName = metadata[geoMetadataIndex]["WMS Layer Name"];
                    var wmsLayerUrl = metadata[geoMetadataIndex]["WMS Layer URL"];

                    if (wmsUrl != null && layerName != null && wmsLayerUrl != null) {
                        console.log("found layer " + wmsUrl);
                        addLayer(this.fileId, this.fileName, wmsUrl, layerName, wmsLayerUrl, current_coord);
                    }
				});
			}
		});
	});


	function addLayer(fileId, fileName, wmsUrl, layerName, wmsLayerUrl, current_coord) {

		if ($('#geospatialDatasetPreviewerMap').length == 0) {
			$(Configuration.div).append("<h4>Geospatial Layers</h4>");

			// adding css for ol3
			var cssLink = $("<link rel='stylesheet' type='text/css' href='http://openlayers.org/en/v3.0.0/css/ol.css'>");
			$(Configuration.div).append(cssLink);

			// adding map div for rendering the map
			var mapDiv = "<div id='geospatialDatasetPreviewerMap' style='height:400px;width:100%'></div>"
			$(Configuration.div).append(mapDiv);

			// adding div for layer control
			var layerControlDiv = "<div id='toolbox' style='position:absolute; top:55px; right:15px; padding:3px; border-radius:4px; color:#fff; background: rgba(255, 255, 255, 0.4); z-index:100;' >";
			layerControlDiv += "<div id='control-title' style='color:black; cursor:pointer;'><span class='glyphicon glyphicon-tasks'></span>&nbsp;Geospatial Layers</div>";
			layerControlDiv += "<div id='layer-control' style='margin:0; padding:10px; border-radius:4px; background:rgba(0, 60, 136, 0.5);'></div></div>";
			$(Configuration.div).append(layerControlDiv);

			// adding event handler for collapsible div
			$('#control-title').click(function () {
				$('#layer-control').toggle();
			});

			// initiating map
			window.map = new ol.Map({
				target: 'geospatialDatasetPreviewerMap'
			});

			// setting up base layer with OSM
			var baseLayer = new ol.layer.Tile({
				source: new ol.source.OSM()
			});

			// adding the base layer to the map
			map.addLayer(baseLayer);

            // adding control box entry for the basemap (OSM)
            var defaultOpacity = 0.5;
            var visVar = 'osm-visible';
            var opVar = 'osm-opacity';

            // add checkbox and range input
            var layerControl = '<div><input id="' + visVar + '" type="checkbox" checked="checked" />Basemap:&nbsp;&nbsp';
            layerControl += '<input id="' + opVar + '" type="range" min="0" max="1" step="0.01" value="' + defaultOpacity + '" style="width:100px;"/></div>';

            // prepend the layer not "append" since the top item means the layer on top
            $('#layer-control').prepend(layerControl);

            // event handler for layer on/off
            $("#" + visVar).change(function () {
                if ($(this).is(':checked')) {
                    baseLayer.setVisible(true);
                } else {
                    baseLayer.setVisible(false);
                }
            });

            // event handler for layer opacity
            $("#" + opVar).change(function () {
                baseLayer.setOpacity($(this).val());
            });
		}

		if (map != null) {
			console.log("adding new layer to map");
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

			console.log("bounding box " + current_coord);

			var defaultOpacity = 0.5;

			// create wms layer
			var wmsLayer = new ol.layer.Tile({
				source: new ol.source.TileWMS(({
					url: wmsUrl,
					params: {
						'LAYERS': layerName,
						'TILED': true
					},
					serverType: 'geoserver'
				})),
				opacity: defaultOpacity
			});

			// add wms layer to the map
			map.addLayer(wmsLayer);

			// Populate layer control for each layer

			// create id by using wms layer name
			var visVar = name[1] + '-visible';
			var opVar = name[1] + '-opacity';

			// add checkbox and range input
			var layerControl = '<div><input id="' + visVar + '" type="checkbox" checked="checked" />' +
				' <a href="../files/' + fileId + '/">' + fileName + "</a>";
			layerControl += '<input id="' + opVar + '" type="range" min="0" max="1" step="0.01" value="' + defaultOpacity + '" style="width:100px;"/></div>';

			// prepend the layer not "append" since the top item means the layer on top
			$('#layer-control').prepend(layerControl);

			// event handler for layer on/off
			$("#" + visVar).change(function () {
				if ($(this).is(':checked')) {
					wmsLayer.setVisible(true);
				} else {
					wmsLayer.setVisible(false);
				}
			});

			// event handler for layer opacity
			$("#" + opVar).change(function () {
				wmsLayer.setOpacity($(this).val());
			});

			// create view object to zoom in
			var view = new ol.View();
			view.fitExtent(current_coord, map.getSize());

			// zoom into the layer extent
			map.setView(view);

			// fix for MMDB-1617
			// force to redraw the map
			// TODO the dom selector needs to select the current selector instead of this selection
			$('a[href$="#tab-home"]').on('shown.bs.tab', function (e) {
				map.updateSize();
			});
		}
	}
}(jQuery, Configuration));