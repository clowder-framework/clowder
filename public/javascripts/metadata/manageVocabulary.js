function addVocabulary(data){

  var request = jsRoutes.api.Metadata.addVocabulary().ajax({
    type: 'POST',
    data: JSON.stringify(data),
    contentType: "application/json"
  });

  request.done(function (response, textStatus, jqXHR){
    console.log("Response " + response);

    if(textStatus == "success")
      notify("Metadata added.", "success");
  });
  request.fail(function (jqXHR, textStatus, errorThrown){
    console.error(
      "The following error occured: "+
      textStatus, errorThrown
    );
    notify("ERROR: " + errorThrown +". Metadata not added.", error);
  });
}

function editVocabulary(id, json, element) {
  reset();
  $(".vocabularyAction").text("Edit");
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
  console.log(id);
  console.log(json);
  console.log(element);
}

function reset(element) {
  element = element || $("#cancelButton");
  $(':input').val('');
  $(element).hide();
  $('.vocabularyAction').text('Add');
}