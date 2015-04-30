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

  var instrumentsValid = true;
  var deferredStreams = [];
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
      console.log($(this));
      console.log($(this).valid());
      if (!$(this).valid()) {
        $(this).find('.collapse').collapse('show');
        instrumentsValid = false;
        return false;
      }
    });

    $('.new-stream-tmpl').each(function() {

      $(this).validate({
        ignore: false,
        messages: {
          instrumentName: "You must provide a name for this instrument",
          instrumentID: "You must provide a unique ID for this instrument"
        }
      });
      console.log($(this));
      console.log($(this).valid());
      if (!$(this).valid()) {
        $(this).find('.collapse').collapse('show');
        instrumentsValid = false;
        return false;
      }
    });

    if (!instrumentsValid) {
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
    data.properties.region = $("#sensorRegion").val();

    console.log(data);
    var sensorPUTpromise = deferredPut(mediciUpdateSensorMetadataURL, JSON.stringify(data.properties));

    $.when(sensorPUTpromise).done(function(data) {
        var sensorJSON = data;

        // update existing streams
        $(".single-stream-tmpl").each(function() {

          var streamID = $(this).find(".stream-id").val();
          var streamData = $(this).find(':input').filter(function () {
            return $.trim(this.value).length > 0
          }).serializeJSON({
            parseNumbers: true,
            parseBooleans: true
          });

          var mediciUpdateStreamMetadataURL = jsRoutes.api.Geostreams.patchStreamMetadata(streamID).url;
          var streamDeferred = deferredPut(mediciUpdateStreamMetadataURL, JSON.stringify(streamData));
          deferredStreams.push(streamDeferred);
        });

        // create any new streams
        $('.new-stream-tmpl').each(function() {
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
          console.log(streamJSON);
          var postDeferred = deferredPost(mediciStreamsURL, JSON.stringify(streamJSON));
          deferredStreams.push(postDeferred);
        });


        $.when.apply($, deferredStreams).done(function(data) {
          console.log(data);
          console.log("should redirect now.");
          // redirect to the sensors list
          window.location.href = jsRoutes.controllers.Geostreams.list().url;
        });

      });

  });



  $("#cancelSubmit").click(function(event) {
    event.preventDefault();
    window.location.href = jsRoutes.controllers.Geostreams.list().url
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