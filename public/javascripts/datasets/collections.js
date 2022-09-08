/**
 * Created by lmarini on 3/25/15.
 */

function addToCollection(datasetId) {
    var selectedId = $("#collectionAddSelect").val();
    if (!selectedId) return false;
    var selectedName = $("#collectionAddSelect option:selected").text();
    selectedName = selectedName.replace(/\n/g, "<br>");

    var request = jsRoutes.api.Collections.attachDataset(selectedId, datasetId).ajax({
        type: 'POST'
    });

    request.done(function (response, textStatus, jqXHR) {
        var o =$.parseJSON(jqXHR.responseText);
        // TODO retrieve more information about collection from API and show it in the GUI
        var txt = '<div id="col_'+selectedId+'" class="row bottom-padding">' +
            '<div class="col-md-2">' +'<a href="'+jsRoutes.controllers.Collections.collection(selectedId).url+'">' +
            '<span class="smallicon glyphicon glyphicon-th-large"></span></a></div>' +
            '<div class="col-md-10">' +
            '<div><a href="'+jsRoutes.controllers.Collections.collection(selectedId).url+'" id='+selectedId+' class ="collection">'+selectedName+'</a> ' +
            '</div>';
        txt = txt + '<div>';
        if (o.datasetsInCollection == 1) {
            txt = txt + o.datasetsInCollection +' dataset';
        } else {
            txt = txt + o.datasetsInCollection +' datasets';
        }
        txt = txt + ' | <button class="btn btn-link btn-xs" onclick="confirmRemoveResourceFromResourceEvent(\'collection\',\'collection\',\''+selectedId+'\',\'dataset\',\''+datasetId+'\', event)" title="Remove the dataset from the collection">' +
        '<span class="glyphicon glyphicon-remove"></span> Remove</button>';
        txt = txt + '</div>';
        txt = txt + '</div>';
        txt = txt + '</div>';
        $("#collectionsList").append(txt);
        $("#collectionAddSelect").select2("val", "");
    });

    request.fail(function (jqXHR, textStatus, errorThrown){
        console.error("The following error occurred: " + textStatus, errorThrown);
        var errMsg = "You must be logged in to add a dataset to a collection.";
        if (!checkErrorAndRedirect(jqXHR, errMsg)) {
            notify("The dataset was not added to the collection due to the following : " + errorThrown, "error");
        }
    });

    return false;
}

function removeFromCollection(collectionId, datasetId, event){
    var request = jsRoutes.api.Collections.removeDataset(collectionId, datasetId, "True").ajax({
        type: 'POST'
    });

    request.done(function (response, textStatus, jqXHR){
        $('#col_'+collectionId).remove();
    });

    request.fail(function (jqXHR, textStatus, errorThrown){
        console.error("The following error occurred: " + textStatus, errorThrown);
        var errMsg = "You must be logged in to remove a dataset from a collection.";
        if (!checkErrorAndRedirect(jqXHR, errMsg)) {
            notify("The dataset was not removed from the collection due to : " + errorThrown, "error");
        }
    });
    return false;
}

//Method to remove the dataset from collection and redirect back to a specific URL on completion
function removeDatasetFromCollectionAndRedirect(collectionId, datasetId, isreload, url){
    var request = jsRoutes.api.Collections.removeDataset(collectionId, datasetId).ajax({
        type: 'POST'
    });

    request.done(function (response, textStatus, jqXHR){
        console.log(collectionId, datasetId, isreload, url);
        if(isreload === "true")
            if(url === undefined) {
                reloadPage = "/collections";
            } else {
                window.location.href=url;
            }
        else {
            $('#col_' + collectionId).remove();
        }
    });

    request.fail(function (jqXHR, textStatus, errorThrown){
        console.error("The following error occurred: " + textStatus, errorThrown);
        var errMsg = "You must be logged in to remove a dataset from a collection.";
        if (!checkErrorAndRedirect(jqXHR, errMsg)) {
            notify("The dataset was not removed from the collection due to : " + errorThrown, "error");
        }
    });
}