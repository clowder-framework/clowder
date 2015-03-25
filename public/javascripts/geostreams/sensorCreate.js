$(document).ready(function() {

  // this function performs a GET request on a specified "url"
  // and optionally a parameters object
  // and returns immediately with a deferred object
  var deferredGet = function(url, parameters) {

    var deferred = $.Deferred();

    $.get( url, parameters, function() {
      deferred.notify( "deferred notify GET for " + url );
    })
      .done(function(data) {
        deferred.notify( "deferred notify done for " + url );
        deferred.resolve( data );
      })
      .fail(function(err) {
        deferred.notify( "deferred notify fail for " + url);
        deferred.reject(err)
      });

    return deferred.promise();
  };

  // this function performs a POST request on a specified "url"
  // with specified "data" and returns immediately with a deferred object
  var deferredPost = function(url, data) {

    var deferred = $.Deferred();

    $.ajax( {
      url: url,
      type: 'POST',
      contentType: 'application/json',
      data: data
    })
      .done(function(data) {
        deferred.notify( "deferred notify done for " + url );
        deferred.resolve( data );
      })
      .fail(function(err) {
        deferred.notify( "deferred notify fail for " + url);
        deferred.reject(err)
      });

    return deferred.promise();
  };

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
        sensorLink1.text('Instrument #1 Information');
        addSensor.show();
        break;
      default:
        hasDepth.hide();
        sensorTypeSensorCount.text('1');
        sensorTypeMultipleSensors.text('');
        sensorContents1.collapse('show');
        sensorLink1.text('Instrument Information');
        addSensor.hide();
        break;
    }
  });

  // enable tooltips
  $('[data-toggle="tooltip"]').tooltip();

  var sensorsValid = true;
  $("#formSubmit").click(function(event) {
    event.preventDefault();
    if (!sensorForm.valid()) {
      return;
    }
    $('.single-stream-tmpl').each(function() {

      $(this).validate({
        ignore: false,
        messages: {
          sensorName: "You must provide a name for this instrument",
          sensorID: "You must provide a unique ID for this instrument"
        }
      });
      console.log($(this));
      console.log($(this).valid());
      if (!$(this).valid()) {
        $(this).find('.collapse').collapse('show');
        sensorsValid = false;
        return false;
      }
    });

    if (!sensorsValid) {
      return;
    }


    var mediciSensorsURL = jsRoutes.api.Geostreams.searchSensors().url;
    var mediciStreamsURL = jsRoutes.api.Geostreams.searchStreams().url;
    var data = {geometry: { type: "Point", coordinates: [0,0,0]}, properties: { type: {id: "", "title": ""}}, type: "Feature"};
    data.name = $("#sensor_name").val();
    data.properties.name = data.name;
    data.properties.popupContent = $("#sensorFullName").val();
    data.geometry.coordinates[0] = +$("#sensorLocationLong").val();
    data.geometry.coordinates[1] = +$("#sensorLocationLat").val();
    data.properties.type.id = $("#sensorDataSource").val().toLowerCase();
    data.properties.type.title = $("#sensorDataSource").val();
    data.properties.region = $("#sensorRegion").val();

    console.log(data);
    var sensorPOSTpromise = deferredPost(mediciSensorsURL, JSON.stringify(data));

    var deferredStreams = [];

    $.when(sensorPOSTpromise).done(function() {
      var sensorGETpromise = deferredGet(mediciSensorsURL + '?geocode=' + data.geometry.coordinates[1] + ',' + data.geometry.coordinates[0] + ',0');
      $.when(sensorGETpromise).done(function(sensorData) {
        console.log(sensorData);
        var sensorJSON = sensorData[0];
        $(".single-stream-tmpl").each(function() {

          var streamJSON = {};
          var streamData = $(this).find(':input').filter(function() {return $.trim(this.value).length > 0}).serializeJSON({
            parseNumbers: true,
            parseBooleans: true
          });

          streamJSON['name'] = streamData['sensorName'];
          delete streamData['sensorName'];
          streamJSON['properties'] = streamData;

          streamJSON['geometry'] = sensorJSON['geometry'];
          streamJSON['sensor_id'] = sensorJSON['id'].toString();
          streamJSON['type'] = sensorJSON['type'];
          console.log(streamJSON);
          deferredStreams.push(deferredPost(mediciStreamsURL, JSON.stringify(streamJSON)));
        });
      });
    });

    $.when(deferredStreams.join(',')).done(function() {
        console.log("should redirect now.");
        // redirect removing the "/new" from the current href
        // necessary until we add the Geostreams to the @controllers
        window.location.href = window.location.href.substring(0, window.location.href.length - 4);
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