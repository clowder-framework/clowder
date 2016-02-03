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


  // this function performs a PUT request on a specified "url"
  // with specified "data" and returns immediately with a deferred object
  var deferredPut = function(url, data) {

    var deferred = $.Deferred();

    $.ajax( {
      url: url,
      type: 'PUT',
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


  // set the link to sensor types dynamically - TODO store this in the sensor config
  var sensorTypesUrl = "https://opensource.ncsa.illinois.edu/confluence/display/IMLCZO/Data+Types";
  var sensorTypesUrlElement = $("#sensorTypesUrl");
  sensorTypesUrlElement.attr('href', sensorTypesUrl);

  // set the sensor types dynamically - TODO store this in the sensor config
  var sensorTypes = {
    1: "1 Instrument, 1 Measurement, No Depth, No Time-Series",
    2: "1 Instrument, 1 Measurement, No Depth, Yes Time-Series",
    3: "1 Instrument, Many Measurements, No Depth, No Time-Series",
    4: "1 Instrument, Many Measurements, No Depth, Yes Time-Series",
    5: "Many Instruments, 1 Measurement, Many Depths, Yes Time-Series",
    6: "Many Instruments, Many Measurements, Many Depths, Yes Time-Series",
    7: "1 Instrument, Many Measurements, One Depth, Yes Time-Series"
  };

  var sensorType = $("#sensorType");
  var selectedSensorType = sensorType.val();
  sensorType.empty();
  $.each(sensorTypes, function(key, value) {
    var insertOption = $("<option></option>").attr("value", key).text(value);
    if (+key == +selectedSensorType) { insertOption.attr('selected', 'selected'); }
    sensorType.append(insertOption);
  });

  // setup form validation
  var sensorForm = $('#sensor-edit');
  sensorForm.validate({
    messages: {
      sensorFullName: "You must provide a name",
      sensor_name: "You must provide a sensor ID",
      sensorDataSource: "You must identify the provider of this data",
      sensorRegion: "You must specify the region. All sensors are grouped into regions on the dashboard."
    }
  });

  var insertInstrumentForm = function(data) {
    var instrumentTemplate = Handlebars.getTemplate("/assets/templates/sensors/stream-form");
    $("#instruments").append(instrumentTemplate(data));
  };

  var instrumentCounter = 0;
  var addInstrumentButton = $("#addInstrument");
    addInstrumentButton.on('click', instrumentCounter, function() {
    instrumentCounter++;
    insertInstrumentForm();
  });

  $("#instruments").on('click', '.removeInstrument', function() {
    var instrumentNumber = $(this).data('id');
    $("#instrument-" + instrumentNumber).remove();
  });


  // enable tooltips
  $('[data-toggle="tooltip"]').tooltip();

  var instrumentsValid = true;
  var deferredStreams = [];
  $("#formSubmit").click(function(event) {
    event.preventDefault();
    if (!sensorForm.valid()) {
      return;
    }

    var mediciSensorsURL = jsRoutes.api.Geostreams.searchSensors().url;
    var mediciStreamsURL = jsRoutes.api.Geostreams.searchStreams().url;
    var mediciUpdateSensorMetadataURL = jsRoutes.api.Geostreams.updateSensorMetadata($("#sensor-id").val()).url;
    var data = {geometry: { type: "Point", coordinates: [0,0,0]}, properties: { type: {id: "", "title": ""}}, type: "Feature"};
    data.name = $("#sensor_name").val();
    data.properties.name = data.name;
    data.properties.popupContent = $("#sensorFullName").val();
    data.geometry.coordinates[0] = +$("#sensorLocationLong").val();
    data.geometry.coordinates[1] = +$("#sensorLocationLat").val();
    data.properties.type.id = $("#sensorDataSource").val().toLowerCase();
    data.properties.type.title = $("#sensorDataSource").val();
    data.properties.type.sensorType = +$("#sensorType").val();
    data.properties.region = $("#sensorRegion").val();

    var sensorPUTpromise = deferredPut(mediciUpdateSensorMetadataURL, JSON.stringify(data.properties));

    $.when(sensorPUTpromise).done(function(data) {
        var sensorJSON = data;

        // update or create streams
        $('.stream-tmpl').each(function() {
          var streamJSON = {};
          var streamData = $(this).find(':input').filter(function() {return $.trim(this.value).length > 0}).serializeJSON({
            parseNumbers: true,
            parseBooleans: true
          });

          streamJSON['name'] = streamData['instrumentName'];
          delete streamData['instrumentName'];
          if (streamData['id']) {
            streamJSON['id'] = streamData['id'];
            delete streamData['id'];
          }

          streamJSON['properties'] = streamData;
          streamJSON['geometry'] = sensorJSON['geometry'];
          streamJSON['sensor_id'] = sensorJSON['id'].toString();
          streamJSON['type'] = sensorJSON['type'];
          if (streamJSON.id) {
            var mediciUpdateStreamMetadataURL = jsRoutes.api.Geostreams.patchStreamMetadata(streamJSON.id).url;
            var streamDeferred = deferredPut(mediciUpdateStreamMetadataURL, JSON.stringify(streamJSON.properties));
            deferredStreams.push(streamDeferred);
          } else {
            var postDeferred = deferredPost(mediciStreamsURL, JSON.stringify(streamJSON));
            deferredStreams.push(postDeferred);
          }

        });

        $.when.apply($, deferredStreams).done(function(data) {
          // redirect to the sensors list
          window.location.reload();
        });

      });

  });

  $("#cancelSubmit").click(function(event) {
    event.preventDefault();
    window.location.href = jsRoutes.controllers.Geostreams.list().url
  });
});