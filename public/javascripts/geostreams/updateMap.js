function updateMap(init) {
  if (window.L) {
    if (init == undefined) { init = false; }

    if (init) {
      map = L.map('map', {scrollWheelZoom: false}).setView([39, -90], 5);

      L.tileLayer('http://{s}.tile.osm.org/{z}/{x}/{y}.png', {
        attribution: '&copy; <a href="http://osm.org/copyright">OpenStreetMap</a> contributors'
      }).addTo(map);
    } else if (typeof marker !== "undefined") {
      map.removeLayer(marker);
    }

    var geojson = $('#sensorLocation').val();

    marker = L.geoJson(JSON.parse(geojson), {draggable: false});
    marker.addTo(map);
    map.fitBounds(marker.getBounds())
  } else {
    console.log('no L found');
  }
}

$(document).ready(function() {
  var map = null;
  var marker = null;
  updateMap(true)
});