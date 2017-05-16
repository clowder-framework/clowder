function addDefinition(data, pageURL, spaceId){
  var url = jsRoutes.api.Metadata.addDefinition()
  if(spaceId != "") {
    url = jsRoutes.api.Metadata.addDefinitionToSpace(spaceId);
  }
  if($(".definitionAction").text().indexOf( "Edit") > -1) {
    var id = $('.definitionAction').attr('id');
    var editUrl = jsRoutes.api.Metadata.editDefinition(id);
    if(spaceId != "") {
    	editUrl = jsRoutes.api.Metadata.editDefinition(id, spaceId);
    }
    var request = editUrl.ajax({
      type: 'POST',
      data: JSON.stringify(data),
      contentType: "application/json"
    });

    request.done(function (response, textStatus, jqXHR) {
      if (textStatus == "success") {
        window.location.href = window.location.href.split('#')[0];
      }

    });
    request.fail(function (jqXHR, textStatus, errorThrown) {
      notify("ERROR: " + jqXHR.responseJSON + " Metadata Definition not edited.", "error");
    });
  }
  else {
    var request = url.ajax({
      type: 'POST',
      data: JSON.stringify(data),
      contentType: "application/json"
    });

    request.done(function (response, textStatus, jqXHR) {
      if (textStatus == "success") {
        window.location.href = window.location.href.split('#')[0];
      }

    });
    request.fail(function (jqXHR, textStatus, errorThrown) {
      notify("ERROR: " + jqXHR.responseJSON + " Metadata Definition not added.", "error");
    });
  }
}

function editDefinition(id, json, element) {
  reset();
  $(".definitionAction").text("Edit");
  $(".definitionActionButton").text(" Save");
  $(".glyphicon-plus").attr("class", "glyphicon glyphicon-send");
  $(".definitionAction").attr("id",id);
  json = JSON.parse(json);
  if (json.label) {
    $("#label").val(json.label);
  }
  if (json.description) {
    $("#description").val(json.description);
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
  if (json.query_parameter) {
    $("#query_parameter").val(json.query_parameter);
  }

  $("#cancelButton").show();
}

function reset(element) {
  element = element || $("#cancelButton");
  $(':input').val('');
  $(element).hide();
  $("#validationResults").empty();
  $('.definitionAction').text('Add');
  $(".definitionActionButton").text("Add");
  $(".glyphicon-send").attr("class", "glyphicon glyphicon-plus");
}