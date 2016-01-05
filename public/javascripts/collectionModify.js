
resource_type_enum = {
    DATASET : 0,
    COLLECTION : 1
}


function addCollectionToParentCollection(id) {
    var selectedId = $("#collectionAddSelect").val();
    if (!selectedId) return false;
    var selectedName = $("#collectionAddSelect option:selected").text();
    selectedName = selectedName.replace(/\n/g, "<br>");

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
        txt = txt + ' | <a href="#" class="btn btn-link btn-xs" onclick="removeChildFromParent(\''+selectedId+'\', \''+id+'\', event)" title="Remove from space">' +
            '<span class="glyphicon glyphicon-remove"></span> Remove</a></div></div></div>';
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


function removeCollectionFromSpace(spaceId, id, event){
    /*
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
    */
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
        txt = txt + ' | <a href="#" class="btn btn-link btn-xs" onclick="removeDataset(\''+selectedId+'\', \''+id+'\', event)" title="Remove from space">' +
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
    /*
    var request = jsRoutes.api.Collections.removeSubCollection(parentId, id).ajax({
        type: 'POST'
    });

    request.done(function (response, textStatus, jqXHR){
        $('#col_'+parentId).remove();
    });

    request.fail(function (jqXHR, textStatus, errorThrown){
        console.error("The following error occured: " + textStatus, errorThrown);
        var errMsg = "You must be logged in to remove a dataset from a collection.";
        if (!checkErrorAndRedirect(jqXHR, errMsg)) {
            notify("The dataset was not removed from the collection due to : " + errorThrown, "error");
        }
    });
    return false;
    */
}


function updateCollectionEditLink(collection_id, collection_name) {
    $('#collection_link').attr("href", jsRoutes.controllers.Collections.collection(collection_id).url).text(collection_name);
}


function updateUsersInCollection(collectionId) {
    //Generate the string for each level
    var currRole = null;
    var roleUserMap = {};
    for (var i = 0; i < roleArray.length; i++) {
        currRole = roleArray[i].replace(/ /g, '');
        console.log("roleList[i] is " + roleArray[i] + " and " + currRole);

        var idsCurrent = $('#' + currRole + '-current li a').map(function(){ return this.id }).get().join(',');
        console.log('idsCurrent are ' + idsCurrent);
        var idsSelected = $("#" + currRole + " option:selected").map(function(){ return this.value }).get().join(",");
        console.log("idsSelected are " + idsSelected);

        if (idsCurrent) {
            if (idsSelected) {
                roleUserMap[roleArray[i]] = idsCurrent + ',' + idsSelected;
            } else {
                roleUserMap[roleArray[i]] = idsCurrent;
            }
        } else {
            roleUserMap[roleArray[i]] = idsSelected;
        }
    }

    var jsonData = JSON.stringify({"rolesandusers":roleUserMap});
    var request = jsRoutes.api.Collections.updateUsers(collectionId).ajax({
        data: jsonData,
        type: 'POST',
        contentType: "application/json",
    });

    request.done(function (response, textStatus, jqXHR){
        console.log("Successful response from updateUsers.")
        window.location.reload();
    });


    request.fail(function (jqXHR, textStatus, errorThrown){
        console.error("The following error occurred: " + textStatus, errorThrown);
        var errMsg = "You must be logged in to update the users contained within a space.";

    });

    return false;
}

function acceptCollectionRequest(id, user){
    /*
    var role = $("#roleSelect").val();
    var request = jsRoutes.controllers.Spaces.acceptRequest(id, user, role).ajax({
        type : 'GET',
        contentType : "application/json"
    });
    request.done ( function ( response, textStatus, jqXHR ) {
        $("#request-tr-"+user).hide();
        console.log("Successful accept request");
    });
    request.fail(function(jqXHR, textStatus, errorThrown) {
        console.error("The following error occured: " + textStatus, errorThrown);
        var errMsg = "You must be logged in to accept request.";
    });
    */
    return false;
}

function rejectCollectionRequest(id, user){
    /*
    var request = jsRoutes.controllers.Spaces.rejectRequest(id, user).ajax({
        type : 'GET',
        contentType : "application/json"
    });
    request.done ( function ( response, textStatus, jqXHR ) {
        $("#request-tr-"+user).hide();
        console.log("Successful reject request");
    });
    request.fail(function(jqXHR, textStatus, errorThrown) {
        console.error("The following error occured: " + textStatus, errorThrown);
        var errMsg = "You must be logged in to reject request.";
    });
    */
    return false;
}
