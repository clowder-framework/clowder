/**
 * Created by lmarini on 3/25/15.
 */

function addToCollection(datasetId) {

    var selectedId = $("#collectionAddSelect").val();
    var selectedName = $("#collectionAddSelect option:selected").text();
    selectedName = selectedName.replace(/\n/g, "<br>");

    var request = jsRoutes.api.Collections.attachDataset(selectedId, datasetId).ajax({
        type: 'POST'
    });

    request.done(function (response, textStatus, jqXHR) {
        var o =$.parseJSON(jqXHR.responseText);
        // TODO retrieve more information about collection from API and show it in the GUI
        $("#collectionsList").append('<div id="col_'+selectedId+'" class="row bottom-padding">' +
        '<div class="col-md-2"></div>' +
        '<div class="col-md-10"><div><a href="'+jsRoutes.controllers.Collections.collection(selectedId).url+'" id='+selectedId+' class ="collection">'+selectedName+'</a></div><div>' +
            o.datasetsInCollection+' datasets | <a href="#" class="btn btn-link btn-xs" onclick="removeCollection(\''+selectedId+'\',\''+selectedName+'\', \''+datasetId+'\', event)" title="Remove from collection">' +
        ' Remove</a></div></div></div>');
        $("#collectionAddSelect").select2("val", "");
    });

    request.fail(function (jqXHR, textStatus, errorThrown){
        console.error("The following error occured: " + textStatus, errorThrown);
        var errMsg = "You must be logged in to add a dataset to a collection.";
        if (!checkErrorAndRedirect(jqXHR, errMsg)) {
            notify("The dataset was not added to the collection due to the following : " + errorThrown, "error");
        }
    });

    return false;
}


function removeCollection(collectionId, collectionName, datasetId, event){

    var request = jsRoutes.api.Collections.removeDataset(collectionId, datasetId, "True").ajax({
        type: 'POST'
    });

    request.done(function (response, textStatus, jqXHR){
        $('#col_'+collectionId).remove();
    });

    request.fail(function (jqXHR, textStatus, errorThrown){
        console.error("The following error occured: " + textStatus, errorThrown);
        var errMsg = "You must be logged in to remove a dataset from a collection.";
        if (!checkErrorAndRedirect(jqXHR, errMsg)) {
            notify("The dataset was not removed from the collection due to : " + errorThrown, "error");
        }
    });
    return false;
}

