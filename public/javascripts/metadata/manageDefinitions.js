function addDefinition(data, pageURL){

  var request = jsRoutes.api.Metadata.addDefinition().ajax({
    type: 'POST',
    data: JSON.stringify(data),
    contentType: "application/json"
  });

  request.done(function (response, textStatus, jqXHR){
    if(textStatus == "success") {
      window.location.href = window.location.href.split('#')[0];
    }

  });
  request.fail(function (jqXHR, textStatus, errorThrown){
    console.error(
      "The following error occured: "+
      textStatus, errorThrown
    );
    notify("ERROR: " + errorThrown +". Metadata Definition not added.", error);
  });
}

function editDefinition(id, json, element) {
  reset();
  $(".definitionAction").text("Edit");
  json = JSON.parse(json);
  if (json.label) {
    $("#label").val(json.label);
  }
  if (json.uri) {
    $("#uri").val(json.uri);
  }
  if (json.type) {
    $("#type").val(json.type).change();
  }
  if (json.definitions_url) {
    $("#definitions_url").val(json.definitions_url);
  }

  $("#cancelButton").show();
}

function reset(element) {
  element = element || $("#cancelButton");
  $(':input').val('');
  $(element).hide();
  $("#validationResults").empty();
  $('.definitionAction').text('Add');
}