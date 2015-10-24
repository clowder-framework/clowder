
resource_type_enum = {
    DATASET : 0,
    COLLECTION : 1
}


function addCollectionToSapce(id) {

    var selectedId = $("#spaceAddSelect").val();
    var selectedName = $("#spaceAddSelect option:selected").text();
    selectedName = selectedName.replace(/\n/g, "<br>");

    var request = jsRoutes.api.Spaces.addCollectionToSapce(selectedId, id).ajax({
        type: 'POST'
    });

    request.done(function (response, textStatus, jqXHR) {
        var o =$.parseJSON(jqXHR.responseText);
        $("#spacesList").append('<div id="col_'+selectedId+'" class="row bottom-padding">' +
            '<div class="col-md-2"></div>' +
            '<div class="col-md-10"><div><a href="'+jsRoutes.controllers.Spaces.getSpace(selectedId).url+'" id='+selectedId+' class ="space">'+selectedName+'</a></div><div>' +
            o.collectionInSpace+' collections | <a href="#" class="btn btn-link btn-xs" onclick="removeCollectionFromSpace(\''+selectedId+'\',\''+selectedName+'\', \''+id+'\', event)" title="Remove from space">' +
            ' Remove</a></div></div></div>');
        $("#spaceAddSelect").select2("val", "");
    });

    request.fail(function (jqXHR, textStatus, errorThrown){
        console.error("The following error occured: " + textStatus, errorThrown);
        var errMsg = "You must be logged in to add a collection to a space.";
        if (!checkErrorAndRedirect(jqXHR, errMsg)) {
            notify("The collection was not added to the space due to the following : " + errorThrown, "error");
        }
    });

    return false;
}


function removeCollectionFromSpace(spaceId, spaceName, id, event){

    var request = jsRoutes.api.Spaces.removeCollection(spaceId, id).ajax({
        type: 'POST'
    });

    request.done(function (response, textStatus, jqXHR){
        $('#col_'+spaceId).remove();
    });

    request.fail(function (jqXHR, textStatus, errorThrown){
        console.error("The following error occured: " + textStatus, errorThrown);
        var errMsg = "You must be logged in to remove a collection from a space.";
        if (!checkErrorAndRedirect(jqXHR, errMsg)) {
            notify("The collection was not removed from the space due to : " + errorThrown, "error");
        }
    });
    return false;
}


function addDatasetToSapce(id) {

    var selectedId = $("#spaceAddSelect").val();
    var selectedName = $("#spaceAddSelect option:selected").text();
    selectedName = selectedName.replace(/\n/g, "<br>");

    var request = jsRoutes.api.Spaces.addDatasetToSpace(selectedId, id).ajax({
        type: 'POST'
    });

    request.done(function (response, textStatus, jqXHR) {
        var o =$.parseJSON(jqXHR.responseText);
        $("#spacesList").append('<div id="col_'+selectedId+'" class="row bottom-padding">' +
            '<div class="col-md-2"></div>' +
            '<div class="col-md-10"><div><a href="'+jsRoutes.controllers.Spaces.getSpace(selectedId).url+'" id='+selectedId+' class ="space">'+selectedName+'</a></div><div>' +
            o.datasetsInSpace+' datasets | <a href="#" class="btn btn-link btn-xs" onclick="removeDatasetFromSpace(\''+selectedId+'\',\''+selectedName+'\', \''+id+'\', event)" title="Remove from space">' +
            ' Remove</a></div></div></div>');
        $("#spaceAddSelect").select2("val", "");
    });

    request.fail(function (jqXHR, textStatus, errorThrown){
        console.error("The following error occured: " + textStatus, errorThrown);
        var errMsg = "You must be logged in to add a dataset to a space.";
        if (!checkErrorAndRedirect(jqXHR, errMsg)) {
            notify("The dataset was not added to the space due to the following : " + errorThrown, "error");
        }
    });

    return false;
}


function removeDatasetFromSpace(spaceId, spaceName, id, event){

    var request = jsRoutes.api.Spaces.removeDataset(spaceId, id).ajax({
        type: 'POST'
    });

    request.done(function (response, textStatus, jqXHR){
        $('#col_'+spaceId).remove();
    });

    request.fail(function (jqXHR, textStatus, errorThrown){
        console.error("The following error occured: " + textStatus, errorThrown);
        var errMsg = "You must be logged in to remove a dataset from a space.";
        if (!checkErrorAndRedirect(jqXHR, errMsg)) {
            notify("The dataset was not removed from the space due to : " + errorThrown, "error");
        }
    });
    return false;
}