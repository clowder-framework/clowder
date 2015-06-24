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
  var sensorForm = $('#sensor-create');
  sensorForm.validate({
    messages: {
      sensorFullName: "You must provide a name",
      sensor_name: "You must provide a sensor ID",
      sensorDataSource: "You must identify the provider of this data",
      sensorRegion: "You must specify the region. All sensors are grouped into regions on the dashboard."
    }
  });

  var instrumentCounter = 0;
  var addInstrumentButton = $("#addInstrument");
  addInstrumentButton.on('click', instrumentCounter, function() {
    instrumentCounter++;
    var instrumentTemplate = $("#newInstrumentTemplate").html();
    var template = Handlebars.compile(instrumentTemplate);
    var data = {instrumentNumber: instrumentCounter.toString()};
    var result = template(data);
    $("#instruments").append(result);
  });
  // add the first sensor on page load and click it to open the accordion
  addInstrumentButton.click();
  $("#instrument-link-1").click();

  $("#instruments").on('click', '.removeInstrument', function() {
    console.log($(this).data('id'));
    var instrumentNumber = $(this).data('id');
    $("#instrument-" + instrumentNumber).remove();
  });


  $("#sensorType").on('change', function() {
    var sensorType = $(this).val();
    var hasDepth = $("#hasDepth");
    var sensorTypeSensorCount = $("#sensorTypeSensorCount");
    var sensorTypeMultipleInstruments = $("#sensorTypeMultipleInstruments");
    var instrumentContents1 = $("#instrument-contents-1");
    var instrumentLink1 = $("#instrument-link-1");
    var addInstrument = $("#addInstrument");

    $("#sensorTypeSummary").text(sensorType);
    switch(sensorType) {
      case "5":
      case "6":
      case "7":
        hasDepth.show();
        sensorTypeSensorCount.text('multiple');
        sensorTypeMultipleInstruments.text('s');
        instrumentContents1.collapse('hide');
        instrumentLink1.text('Instrument #1 Information');
        addInstrument.show();
        break;
      default:
        hasDepth.hide();
        sensorTypeSensorCount.text('1');
        sensorTypeMultipleInstruments.text('');
        instrumentContents1.collapse('show');
        instrumentLink1.text('Instrument Information');
        addInstrument.hide();
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
          instrumentName: "You must provide a name for this instrument",
          instrumentID: "You must provide a unique ID for this instrument"
        }
      });
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

    var sensorPOSTpromise = deferredPost(mediciSensorsURL, JSON.stringify(data));

    var deferredStreams = [];

    $.when(sensorPOSTpromise).done(function() {
      var sensorGETpromise = deferredGet(mediciSensorsURL + '?geocode=' + data.geometry.coordinates[1] + ',' + data.geometry.coordinates[0] + ',0');
      $.when(sensorGETpromise).done(function(sensorData) {
        var sensorJSON = sensorData[0];
        $(".single-stream-tmpl").each(function() {

          var streamJSON = {};
          var streamData = $(this).find(':input').filter(function() {return $.trim(this.value).length > 0}).serializeJSON({
            parseNumbers: true,
            parseBooleans: true
          });

          streamJSON['name'] = streamData['instrumentName'];
          delete streamData['instrumentName'];
          streamJSON['properties'] = streamData;

          streamJSON['geometry'] = sensorJSON['geometry'];
          streamJSON['sensor_id'] = sensorJSON['id'].toString();
          streamJSON['type'] = sensorJSON['type'];
          deferredStreams.push(deferredPost(mediciStreamsURL, JSON.stringify(streamJSON)));
        });

        $.when.apply($, deferredStreams).done(function(data) {
          // redirect removing the "/new" from the current href
          // necessary until we add the Geostreams to the @controllers
          window.location.href = window.location.href.substring(0, window.location.href.length - 4);
        });
        
      });
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