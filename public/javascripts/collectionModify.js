
resource_type_enum = {
    DATASET : 0,
    COLLECTION : 1
}

function addCollectionToParentCollection(id) {
    var selectedId = $("#collectionAddSelect").val();
    if (!selectedId) return false;
    var selectedName = $("#collectionAddSelect option:selected").text();
    selectedName = selectedName.replace(/\n/g, "<br>");

    if (id == selectedId){
        notify("A collection can not be added to itself!")
        return false;
    }

    var request = jsRoutes.api.Collections.attachSubCollection(selectedId, id).ajax({
        type: 'POST'
    });

    request.done(function (response, textStatus, jqXHR) {
        var o = $.parseJSON(jqXHR.responseText);
        var txt = '<div id="col_'+selectedId+'" class="row bottom-padding">' +
            '<div class="col-md-2"></div>' +
            '<div class="col-md-10"><div><a href="'+jsRoutes.controllers.Collections.collection(selectedId).url+'" id='+selectedId+' class ="collection">'+selectedName+'</a></div>' +
            '<div>';
        if (o.childCollectionsCount == 1) {
            txt = txt + o.childCollectionsCount +' collection';
        } else {
            txt = txt + o.childCollectionsCount +' collections';
        }
        $("#collectionsList").append(txt);
        $("#collectionAddSelect").select2("val", "");
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

function addDatasetToCollection(id) {
    var selectedId = $("#collectionAddSelect").val();
    if (!selectedId) return false;
    var selectedName = $("#collectionAddSelect option:selected").text();
    selectedName = selectedName.replace(/\n/g, "<br>");

    var request = jsRoutes.api.Collections.attachDataset(selectedId, id).ajax({
        type: 'POST'
    });

    request.done(function (response, textStatus, jqXHR) {
        var o =$.parseJSON(jqXHR.responseText);
        var txt = '<div id="col_'+selectedId+'" class="row bottom-padding">' +
            '<div class="col-md-2"></div>' +
            '<div class="col-md-10"><div><a href="'+jsRoutes.controllers.Collections.collection(selectedId).url+'" id='+selectedId+' class ="collection">'+selectedName+'</a></div>' +
            '<div>';
        if (o.datasetCount == 1) {
            txt = txt + o.datasetCount +' dataset';
        } else {
            txt = txt + o.datasetCount +' datasets';
        }
        txt = txt + ' | <a href="#" class="btn btn-link btn-xs" onclick="confirmRemoveResourceFromResourceEvent(\'collection\',\''+selectedId+'\',\'dataset\',\''+id+'\', event)" title="Remove the dataset from the collection">' +
            '<span class="glyphicon glyphicon-remove"></span> Remove</a></div></div></div>';
        $("#collectionsList").append(txt);
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

function removeChildFromParent(parentId, id, event){
    notify("You must be viewing the parent collection to remove a child");
}

function updateCollectionEditLink(collection_id, collection_name) {
    $('#collection_link').attr("href", jsRoutes.controllers.Collections.collection(collection_id).url).text(collection_name);
}

