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



    function addXYLayer(coord, layerName, layerURL) {
        initializeMap()

        if (map != null) {
            console.log("adding new XY @("+coord[0]+","+coord[1]+") to map - "+layerName);

            /*
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
             */

            var defaultOpacity = 0.5;

            // Create new layer and add to map
            var marker = new ol.Feature({
                geometry: new ol.geom.Point([coord[0],coord[1]])
            })
            console.log(marker)
            var vectorSource = new ol.source.Vector({
                features: [marker]
            })
            var currExtent = vectorSource.getExtent()
            var vectorLayer = new ol.layer.Vector({
                source: vectorSource
            })
            map.addLayer(vectorLayer);

            // Populate layer control for each layer

            // create id by using wms layer name
            var visVar = layerName + '-visible';
            var opVar = layerName + '-opacity';

            // add checkbox and range input
            var layerControl = '<div><input id="' + visVar + '" type="checkbox" checked="checked" /> <a href="'+layerURL+'">'+layerName+'</a>' +
                                '<input id="' + opVar + '" type="range" min="0" max="1" step="0.01" value="' + defaultOpacity + '" style="width:100px;"/></div>';

            // prepend the layer not "append" since the top item means the layer on top
            $('#layer-control').prepend(layerControl);

            // event handler for layer on/off
            $("#" + visVar).change(function () {
                if ($(this).is(':checked')) {
                    vectorLayer.setVisible(true);
                } else {
                    vectorLayer.setVisible(false);
                }
            });

            // event handler for layer opacity
            $("#" + opVar).change(function () {
                vectorLayer.setOpacity($(this).val());
            });

            // create view object to zoom in
            var view = new ol.View();
            view.fitExtent(currExtent, map.getSize());
            map.setView(view);

            // fix for MMDB-1617
            // force to redraw the map
            // TODO the dom selector needs to select the current selector instead of this selection
            $('a[href$="#tab-visua"]').on('shown.bs.tab', function (e) {
                console.log("TAB VISUA")
                map.updateSize();
                view.fitExtent(currExtent, map.getSize());
            });
        }
    }

    function addGeoJSONLayer(geojson, layerName, layerURL) {
        initializeMap()

        if (map != null) {
            console.log("adding new layer to map - "+layerName);

            /*
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
            */

            var defaultOpacity = 0.5;

            // Create new layer and add to map
            vectorSource = new ol.source.Vector({
                features: new ol.format.GeoJSON().readFeatures(geojson, {
                    featureProjection: 'EPSG:3857'
                })
            })

            console.log(vectorSource)

            geojsonExtent = vectorSource.getExtent()
            console.log("GeoJSON Extent")
            console.log(geojsonExtent)

            var vectorLayer = new ol.layer.Vector({
                source: vectorSource
            })
            map.addLayer(vectorLayer);

            // Populate layer control for each layer

            // create id by using wms layer name
            var visVar = layerName + '-visible';
            var opVar = layerName + '-opacity';

            // add checkbox and range input
            var layerControl = '<div><input id="' + visVar + '" type="checkbox" checked="checked" />' +
                ' <a href="'+layerURL+'">'+layerName+'</a>';
            layerControl += '<input id="' + opVar + '" type="range" min="0" max="1" step="0.01" value="' + defaultOpacity + '" style="width:100px;"/></div>';

            // prepend the layer not "append" since the top item means the layer on top
            $('#layer-control').prepend(layerControl);

            // event handler for layer on/off
            $("#" + visVar).change(function () {
                if ($(this).is(':checked')) {
                    vectorLayer.setVisible(true);
                } else {
                    vectorLayer.setVisible(false);
                }
            });

            // event handler for layer opacity
            $("#" + opVar).change(function () {
                vectorLayer.setOpacity($(this).val());
            });

            // create view object to zoom in
            var view = new ol.View();
            view.fitExtent([0,0,90,90], map.getSize());

            // zoom into the layer extent
            map.setView(view);

            // fix for MMDB-1617
            // force to redraw the map
            // TODO the dom selector needs to select the current selector instead of this selection
            $('a[href$="#tab-visua"]').on('shown.bs.tab', function (e) {
                map.updateSize();
                //view.fitExtent(current_coord, map.getSize());
            });
        }
    }

    function initializeMap() {
        if ($('#geospatialGeoJSONPreviewerMap').length == 0) {
            $(Configuration.div).append("<h4>GeoJSON & XY Data</h4>");

            // adding css for ol3
            var cssLink = $("<link rel='stylesheet' type='text/css' href='" + Configuration.path + "/../../../openlayers/ol.css'>");
            $(Configuration.div).append(cssLink);

            // adding map div for rendering the map
            var mapDiv = "<div id='geospatialGeoJSONPreviewerMap' style='height:400px;width:100%'></div>"
            $(Configuration.div).append(mapDiv);

            // adding div for layer control
            var layerControlDiv = "<div id='toolbox' style='position:absolute; top:55px; right:15px; padding:3px; border-radius:4px; color:#fff; background: rgba(255, 255, 255, 0.4); z-index:100;' >";
            layerControlDiv += "<div id='control-title' style='color:black; cursor:pointer;'><span class='glyphicon glyphicon-tasks'></span>&nbsp;GeoJSON & XY Data</div>";
            layerControlDiv += "<div id='layer-control' style='margin:0; padding:10px; border-radius:4px; background:rgba(0, 60, 136, 0.5);'></div></div>";
            $(Configuration.div).append(layerControlDiv);

            // adding event handler for collapsible div
            $('#control-title').click(function () {
                $('#layer-control').toggle();
            });

            // initiating map
            window.map = new ol.Map({
                target: 'geospatialGeoJSONPreviewerMap',
                projection: 'EPSG:3857'
            });

            // setting up base layer with OSM
            var baseLayer = new ol.layer.Tile({
                source: new ol.source.OSM()
            });
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
    }

    console.log("LATLON PREVIEWER")
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
                    dataType: "json"
                });

                file_md_req.done(function(file_data) {
                    console.log("GOT METADATA")
                    console.log(file_data)

                    if (file_data.length > 0) {
                        var file_url = jsRoutes.controllers.Files.file(data[file_details]["id"]).url

                        // Check for geojson members in the metadata, and add to map
                        var geojson = checkForGeoJSON(file_data, file_url);
                        console.log("GEOJSON LIST")
                        console.log(geojson)

                        if (geojson.length > 0) {
                            addGeoJSONLayer(geojson, data[file_details]["filename"], file_url)
                        }

                        // Check for lat/lon members in the metadata, and add to map
                        var coords = checkForLatLng(file_data);
                        console.log("COORDINATES LIST")
                        console.log(coords)

                        if (coords.length > 0) {
                            for (var c in coords){
                                addXYLayer(coords[c],data[file_details]["filename"], file_url)
                            }
                        }
                    }
                });
            }
        }
    });
}(jQuery, Configuration));