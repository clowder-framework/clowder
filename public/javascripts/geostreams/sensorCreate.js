$(document).ready(function() {

  // setup form validation
  var sensorForm = $('#new');
  sensorForm.validate({
    messages: {
      sensorFullName: "You must provide a name",
      sensor_name: "You must provide a sensor ID",
      sensorDataSource: "You must identify the provider of this data",
      sensorRegion: "You must specify the region. All sensors are grouped into regions on the dashboard."
    }
  });

  var sensorCounter = 0;
  var addSensorButton = $("#addSensor");
  addSensorButton.on('click', sensorCounter, function() {
    sensorCounter++;
    var sensorTemplate = $("#newSensorTemplate").html();
    var template = Handlebars.compile(sensorTemplate);
    var data = {sensorNumber: sensorCounter.toString()};
    var result = template(data);
    $("#additionalSensors").append(result);
  });
  // add the first sensor on page load and click it to open the accordion
  addSensorButton.click();
  $("#sensor-link-1").click();

  $("#additionalSensors").on('click', '.removeSensor', function() {
    console.log($(this).data('id'));
    var sensorNumber = $(this).data('id');
    $("#sensor-" + sensorNumber).remove();
  });


  $("#sensorType").on('change', function() {
    var sensorType = $(this).val();
    var hasDepth = $("#hasDepth");
    var sensorTypeSensorCount = $("#sensorTypeSensorCount");
    var sensorTypeMultipleSensors = $("#sensorTypeMultipleSensors");
    var sensorContents1 = $("#sensor-contents-1");
    var sensorLink1 = $("#sensor-link-1");
    var addSensor = $("#addSensor");

    $("#sensorTypeSummary").text(sensorType);
    switch(sensorType) {
      case "5":
      case "6":
      case "7":
        hasDepth.show();
        sensorTypeSensorCount.text('multiple');
        sensorTypeMultipleSensors.text('s');
        sensorContents1.collapse('hide');
        sensorLink1.text('Sensor #1 Information');
        addSensor.show();
        break;
      default:
        hasDepth.hide();
        sensorTypeSensorCount.text('1');
        sensorTypeMultipleSensors.text('');
        sensorContents1.collapse('show');
        sensorLink1.text('Sensor Information');
        addSensor.hide();
        break;
    }
  });

  // enable tooltips
  $('[data-toggle="tooltip"]').tooltip();


  $("#formSubmit").click(function(event) {
    event.preventDefault();
    var mediciSensorsURL = window.location.protocol + "//" + window.location.hostname + (window.location.port ? ':' + window.location.port: '') + "/api/geostreams/sensors";
    var data = {geometry: { type: "Point", coordinates: [0,0,0]}, properties: { type: {id: "", "title": ""}}, type: "Feature"};
    data.name = $("#sensor_name").val();
    data.properties.name = data.name;
    data.properties.popupContent = $("#sensorFullName").val();
    data.geometry.coordinates[0] = +$("#sensorLocationLong").val();
    data.geometry.coordinates[1] = +$("#sensorLocationLat").val();
    data.properties.type.id = $("#sensorDataSource").val().toLowerCase();
    data.properties.type.title = $("#sensorDataSource").val();
    data.properties.region = $("#sensorRegion").val();
    $.ajax({
      url: mediciSensorsURL,
      type: 'POST',
      contentType: 'application/json',
      data: JSON.stringify(data)
    })
      .done(function() {
        $.ajax( {
          url : mediciSensorsURL,
          type : 'GET',
          data : 'geocode=' + data.geometry.coordinates[1] + ',' + data.geometry.coordinates[0] + ',' + data.geometry.coordinates[2]
        })
          .done(function(data){
            var url = window.location.protocol + "//" + window.location.hostname + (window.location.port ? ':' + window.location.port: '') + "/geostreams/sensors/";
            var sensorID = (data[0] && data[0].id) ? data[0].id : null;
            if (sensorID) {
              window.location.href = url + sensorID;
            }

          });
      })
      .fail(function() {
        console.log('failed to create sensor')
      });
  });

  if (window.L) {
    var map = L.map('map', {scrollWheelZoom: false}).setView([39, -90 ], 5);

    L.tileLayer('http://{s}.tile.osm.org/{z}/{x}/{y}.png', {
      attribution: '&copy; <a href="http://osm.org/copyright">OpenStreetMap</a> contributors'
    }).addTo(map);

    var marker = L.marker([39, -90], {draggable: true});
    marker.addTo(map);
    marker.on('dragend', function(event){
      $('#sensorLocationLat').val(event.target._latlng.lat);
      $('#sensorLocationLong').val(event.target._latlng.lng);
    })
  } else {
    console.log('no L found');
  }
});