(function($, Configuration) {

    function checkForLatLng(json_obj) {
        /**
         * Iterate through object looking for lat/lon data under several possible fields
         */
        var coords_found = [];
        Object.keys(json_obj).forEach(function(key){
            var lat=null, lon=null;
            var lat_names = ["latitude", "lat", "y"];
            var lon_names = ["longitude", "lon", "long", "x"];

            for (var l in lat_names) {
                if (json_obj[key].hasOwnProperty(lat_names[l])) {
                    lat = json_obj[key][lat_names[l]];
                    break;
                }
            }
            for (var l in lon_names) {
                if (json_obj[key].hasOwnProperty(lon_names[l])) {
                    lon = json_obj[key][lon_names[l]];
                    break;
                }
            }

            if (lat != null && lon != null) {
                coords_found.push([lat,lon]);
            }
        });
        return coords_found;
    }

    function checkForGeoJSON(json_obj) {
        /**
         * Iterate through object looking for geojson data field and return a list of GeoJSON objects
         */
        var geojson_found = [];
        // We can load a FeatureCollection directly
        if (json_obj.hasOwnProperty("features") &&
            json_obj.hasOwnProperty("type") && json_obj["type"] == "FeatureCollection") {
            geojson_found.push(json_obj);
        }
        // We can load a Feature directly, but not a primitive geometry (e.g. Point) or GeometryCollection
        else if (json_obj.hasOwnProperty("geometry") &&
            json_obj.hasOwnProperty("type") && json_obj["type"] == "Feature") {
            geojson_found.push(json_obj);
        }
        // If we haven't found any GeoJSON, we keep looking deeper in the object
        else {
            if (typeof json_obj == 'object') {
                Object.keys(json_obj).forEach(function (key) {
                    geojson_found = geojson_found.concat(checkForGeoJSON(json_obj[key]));
                });
            }
        }
        return geojson_found;
    }

    function initializeMap() {
        if ($('#geospatialGeoJSONPreviewerMap').length == 0) {

            // CREATE MAP DIV
            $(Configuration.div).append("<h4>Geospatial Metadata Map</h4>");
            var cssLink = $("<link rel='stylesheet' type='text/css' href='" + Configuration.path + "/../../../openlayers/ol.css'>");
            $(Configuration.div).append(cssLink);
            var mapDiv = "<div id='geospatialGeoJSONPreviewerMap' style='height:400px;width:100%'></div>"
            $(Configuration.div).append(mapDiv);

            // LAYER CONTROL
            var layerControlDiv = "<div id='toolbox' style='position:absolute; top:55px; right:15px; padding:3px; border-radius:4px; color:#fff; background: rgba(255, 255, 255, 0.4); z-index:100;' >";
            layerControlDiv += "<div id='layer-control' style='margin:0; padding:10px; border-radius:4px; background:rgba(0, 60, 136, 0.5);'></div></div>";
            $(Configuration.div).append(layerControlDiv);

            // initiating map
            window.map = new ol.Map({
                target: 'geospatialGeoJSONPreviewerMap',
                projection: "EPSG:3857",
                displayProjection: "EPSG:3857"
            });

            // BASE OSM LAYER
            var baseLayer = new ol.layer.Tile({
                source: new ol.source.OSM()
            });
            map.addLayer(baseLayer);

            // adding control box entry for the basemap (OSM)
            var defaultOpacity = 0.5;
            var visVar = 'osm-visible';
            var opVar = 'osm-opacity';
            var layerControl = '<div><input id="' + visVar + '" type="checkbox" checked="checked" />&nbsp;Basemap';
            layerControl += '<input id="' + opVar + '" type="range" min="0" max="1" step="0.01" value="' + defaultOpacity + '" style="width:100px;"/></div>';
            $('#layer-control').prepend(layerControl);
            // event handler for layer on/off & opacity
            $("#" + visVar).change(function () {
                baseLayer.setVisible($(this).is(':checked'));
            });
            $("#" + opVar).change(function () {
                baseLayer.setOpacity($(this).val());
            });

            // SET UP SELECTION
            var selectColor = '#00FFFF';
            var selectFill  = '#00FFFF';
            var selectStyle = new ol.style.Style({
                image: new ol.style.Circle({
                    fill: new ol.style.Fill({color:selectColor}),
                    radius: 4
                }),
                fill: new ol.style.Fill({color:selectFill}),
                stroke: new ol.style.Stroke({
                    color: selectColor,
                    width: 2
                })
            });
            var selector = new ol.interaction.Select({
                layers: function(layer) {
                    return layer.get('selectable') == true;
                },
                style: [selectStyle]
            });
            map.getInteractions().extend([selector]);

            // SET SELECTION POPUP
            var popupDiv = '<div style="background:#FFFFFF;color:#000000;padding:5px;" id="popup-div"></div>'
            $(Configuration.div).append(popupDiv);
            var overlay = new ol.Overlay({
                element: document.getElementById('popup-div'),
                autoPan: true,
                autoPanAnimation: {
                    duration: 250
                }
            });
            map.addOverlay(overlay);
            map.on('click', function(evt){
                var foundTarget = false;
                map.forEachFeatureAtPixel(evt.pixel, function(feat, lyr) {
                    document.getElementById("popup-div").innerHTML = '<a href="'+feat.get('url')+'"><b>'+feat.get('name')+'</b></a>';
                    overlay.setPosition(evt.coordinate);
                    foundTarget = true;
                });

                if (!foundTarget) {
                    document.getElementById("popup-div").innerHTML = '';
                    overlay.setPosition(undefined);
                }
            });

            // fix for MMDB-1617
            // SET UP VIEW REFRESH
            // TODO the dom selector needs to select the current selector instead of this selection
            $('a[href$="#tab-visua"]').on('shown.bs.tab', function (e) {
                map.updateSize();

                // Fit map extent to existing layers
                var extent = ol.extent.createEmpty();
                map.getLayers().forEach(function(layer) {
                    var layerEx = layer.getExtent()
                    if (typeof layerEx !== 'undefined') {
                        ol.extent.extend(extent, layerEx);
                    }
                });
                map.getView().fitExtent(extent, map.getSize());

                var zm = map.getView().getZoom()
                if (zm > 16) {
                    map.getView().setZoom(16)
                }
            });
        }
    }

    function addXYLayer(latLonList, layerName, layerURL, coordProjection) {
        initializeMap()

        if (map != null) {
            var coordProjection = coordProjection || "EPSG:4326"
            var layerColor = "#FF0000"
            var layerFill  = "#FF8888"

            // CREATE LAYER
            var layerFeats = []
            for (var c in latLonList) {
                var latLon = latLonList[c]
                layerFeats.push(new ol.Feature({
                    geometry: new ol.geom.Point(
                        ol.proj.transform([latLon[1],latLon[0]],coordProjection,"EPSG:3857")),
                    name: layerName,
                    url: layerURL
                }))
            }
            var vectorSource = new ol.source.Vector({
                features: layerFeats
            });
            var vectorLayer = new ol.layer.Vector({
                source: vectorSource,
                extent: vectorSource.getExtent(),
                style: new ol.style.Style({
                    image: new ol.style.Circle({
                        fill: new ol.style.Fill({color:layerColor}),
                        radius: 4
                    }),
                    fill: new ol.style.Fill({color:layerFill}),
                    stroke: new ol.style.Stroke({
                        color: layerColor,
                        width: 2
                    })
                })
            });
            vectorLayer.set('selectable', true);
            map.addLayer(vectorLayer);

            // ADJUST VIEW
            var view = new ol.View();
            view.fitExtent(vectorSource.getExtent(), map.getSize());
            map.setView(view)
        }
    }

    function addGeoJSONLayer(geojson, layerName, layerURL, jsonProjection) {
        initializeMap()

        if (map != null) {
            var jsonProjection = jsonProjection || "EPSG:3857";
            var layerColor = "#FF0000"
            var layerFill  = "#FF8888"

            // CREATE LAYER
            var allFeats = new Array();
            for (var gj in geojson) {
                var fts = new ol.format.GeoJSON().readFeatures(geojson[gj], {
                    featureProjection: jsonProjection
                });
                for (var currft in fts) {
                    fts[currft].set('name', layerName);
                    fts[currft].set('url', layerURL);
                }

                allFeats = allFeats.concat(fts);
            }

            var vectorSource = new ol.source.Vector({
                format: new ol.format.GeoJSON(),
                features: allFeats,
                featureProjection: jsonProjection
            });
            var vectorLayer = new ol.layer.Vector({
                source: vectorSource,
                extent: vectorSource.getExtent(),
                style: new ol.style.Style({
                    image: new ol.style.Circle({
                        fill: new ol.style.Fill({color:layerColor}),
                        radius: 4
                    }),
                    fill: new ol.style.Fill({color:layerFill}),
                    stroke: new ol.style.Stroke({
                        color: layerColor,
                        width: 1
                    })
                })
            });
            vectorLayer.set('selectable', true);
            map.addLayer(vectorLayer);

            // create view object to zoom in
            var view = new ol.View();
            view.fitExtent(vectorSource.getExtent(), map.getSize());
            map.setView(view);
        }
    }

    function buildPopupContentFromJSON(json_obj, leading) {
        /**
         * Take a JSON object and create some nice display HTML for it.
         * @param leading allows one to specify indents, mostly used for recursive sub-objects
         */
        var indent = '&nbsp;&nbsp;&nbsp;&nbsp;';
        var content = '';
        leading = leading || '';

        // GMaps creates the ["O"] sub-object structure on GeoJSON load
        if (json_obj.hasOwnProperty("O")) {
            // Put link to [_file_url] at the top, with [title] as link text if we find it
            if (json_obj["O"].hasOwnProperty("_file_url")) {
                content = leading+'<a href="'+json_obj["O"]["_file_url"]+'"><b>'
                if (json_obj["O"].hasOwnProperty("title")) {
                    content = content.concat(json_obj["O"]["title"]);
                } else {
                    content = content.concat("View File in Clowder");
                }
                content = content.concat('</b></a></br></br>');
                // Or just put the title if we don't have a [_file_url]
            } else {
                if (json_obj["O"].hasOwnProperty("title")) {
                    content = leading+'<b>'+json_obj["O"]["title"]+'</b></br></br>';
                }
            }

            // Add thumbnail below title if it's available
            if (json_obj["O"].hasOwnProperty("_thumbnail")) {
                content = content.concat('<img class="img-thumbnail ds-coords-img-tmb" src="'+json_obj["O"]["_thumbnail"]+'"></br></br>');
            }

            // Format the JSON object for human eyes, indenting sub-objects where we find them
            for (p in json_obj["O"]) {
                if (json_obj["O"][p] instanceof Object) {
                    content = content.concat(leading+'<b>'+p+':</b></br>'+buildPopupContentFromJSON(json_obj["O"][p], leading+indent));
                } else {
                    if (p[0] != "_") { // treat params with leading '_' as hidden
                        content = content.concat(leading + '<b>' + p + ':</b> ' + json_obj["O"][p] + '</br>');
                    }
                }
            }
        } else {
            // If we don't have ["O"] GeoJSON properties, just show everything raw
            for (p in json_obj) {
                if (json_obj[p] instanceof Object) {
                    content = content.concat(leading+'<b>'+p+':</b></br>'+buildPopupContentFromJSON(json_obj[p], leading+indent));
                } else {
                    if (p[0] != "_") { // treat params with leading '_' as hidden
                        content = content.concat(leading+'<b>'+p+':</b> '+json_obj[p]+'</br>');
                    }
                }
            }
        }
        return content;
    }


    var dataset_id = Configuration.dataset_id;

    // Request list of files in this dataset
    var req = $.ajax({
        type: "GET",
        url: jsRoutes.api.Datasets.datasetFilesList(dataset_id).url,
        dataType: "json"
    });

    req.done(function(data){
        // We found at least one file...
        if (data.length > 0){
            // For each file in the dataset...
            for(var file_details in data){
                // Request the technical metadata
                // TODO - replace this with GeoJSON endpoint call
                var file_md_req = $.ajax({
                    type: "GET",
                    url: jsRoutes.api.Files.getTechnicalMetadataJSON(data[file_details]["id"]).url,
                    dataType: "json",
                    fileId: data[file_details]["id"],
                    filename: data[file_details]["filename"]
                });

                file_md_req.done(function(file_data) {
                    if (file_data.length > 0) {
                        var file_url = jsRoutes.controllers.Files.file(this.fileId).url

                        // Check for geojson members in the metadata, and add to map
                        var geojson = checkForGeoJSON(file_data, file_url);
                        if (geojson.length > 0) {
                            addGeoJSONLayer(geojson, this.filename, file_url)
                        }

                        // Check for lat/lon members in the metadata, and add to map
                        var latLonList = checkForLatLng(file_data);
                        if (latLonList.length > 0) {
                            addXYLayer(latLonList,this.filename, file_url)
                        }
                    }
                });


            }
        }

    });
}(jQuery, Configuration));