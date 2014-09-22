(function($, Configuration) {
	console.log("geospatial data previewer for " + Configuration.id);

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

                        // create base layer by using OSM
                        // TODO: create a dropdown box for different backdrop
                        var baseLayer = new ol.layer.Tile({
                            source : new ol.source.OSM()
                        });

                        // create map object
                        var map = new ol.Map({
                            target : 'map'
                        });

                        // add layers
                        map.addLayer(baseLayer);

                        // retrieving the map div size for fit
//                        zheight = $('#map').height() - 100;
//                        zwidth = $('#map').width();
//
//                        console.log('extent:', coord);
//                        console.log('zoom with h:' + zheight
//                                + ', w:' + zwidth);

                        // create view object to zoom in
                        var view = new ol.View({center: [0, 0],zoom: 2});
//                        view.fitExtent(coord, [ zheight, zwidth ]);

                        // zoom into the layer extent
                        map.setView(view);

                    });

}(jQuery, Configuration));