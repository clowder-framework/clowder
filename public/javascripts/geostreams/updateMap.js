if (window.L) {
  var map = L.map('map', {scrollWheelZoom: false}).setView([39, -90 ], 5);

  L.tileLayer('http://{s}.tile.osm.org/{z}/{x}/{y}.png', {
    attribution: '&copy; <a href="http://osm.org/copyright">OpenStreetMap</a> contributors'
  }).addTo(map);

  var longElement = $('#sensorLocationLong');
  var latElement = $('#sensorLocationLat');

  var getCurrentLatLong = function() {
    var longElement = $('#sensorLocationLong');
    var latElement = $('#sensorLocationLat');
    var long = longElement.val();
    long = (long) ? +long : -90;
    var lat = latElement.val();
    lat = (lat) ? +lat : 39;
    return [lat, long];
  };


  var marker = L.marker(getCurrentLatLong(), {draggable: true});
  marker.addTo(map);
  marker.on('dragend', function(event){
    $('#sensorLocationLat').val(event.target._latlng.lat);
    $('#sensorLocationLong').val(event.target._latlng.lng);
  });
  longElement.on('change', function() {
    marker.setLatLng(getCurrentLatLong()).update();
  });
  latElement.on('change', function() {
    marker.setLatLng(getCurrentLatLong()).update();
  });
} else {
  console.log('no L found');
}