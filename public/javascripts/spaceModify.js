
resource_type_enum = {
    DATASET : 0,
    COLLECTION : 1
}


function addCollectionToSpace(id,spaceTitle) {
    var selectedId = $("#spaceAddSelect").val();
    if (!selectedId) return false;
    var selectedName = $("#spaceAddSelect option:selected").text();
    selectedName = selectedName.replace(/\n/g, "<br>");

    var request = jsRoutes.api.Spaces.addCollectionToSpace(selectedId, id).ajax({
        type: 'POST'
    });

    request.done(function (response, textStatus, jqXHR) {
        var o = $.parseJSON(jqXHR.responseText);
        var txt = '<div id="col_'+selectedId+'" class="row bottom-padding">' +
            '<div class="col-md-2"><a href="'+jsRoutes.controllers.Spaces.getSpace(selectedId).url+'">' +
            '<span class="smallicon glyphicon glyphicon-hdd"></span>' +
            '</a></div>' +
            '<div class="col-md-10">' +
            '<div><a href="'+jsRoutes.controllers.Spaces.getSpace(selectedId).url+'" id='+selectedId+' class ="space">'+selectedName+'</a>' +
            '</div>';
        txt = txt + '<div>';
        if (o.collectionInSpace == 1) {
            txt = txt + o.collectionInSpace +' collection';
        } else {
            txt = txt + o.collectionInSpace +' collections';
        }
        txt = txt + ' | <button class="btn btn-link btn-xs" onclick="confirmRemoveResourceFromResourceEvent(\'space\',\''+spaceTitle+'\',\''+selectedId+'\',\'collection\',\''+id+'\', event)" title="Remove the collection">' +
            '<span class="glyphicon glyphicon-remove"></span> Remove</button>';
        txt = txt + '</div>';
        txt = txt + '</div>';
        txt = txt + '</div>';
        $("#spacesList").append(txt);
        $("#spaceAddSelect").select2("val", "");
        if(!isSharingEnabled) {
            $("#add-to-space-widget").addClass("hidden");
        }

    });

    request.fail(function (jqXHR, textStatus, errorThrown){
        console.error("The following error occured: " + textStatus, errorThrown);
        var errMsg = "You must be logged in to add a collection";
        if (!checkErrorAndRedirect(jqXHR, errMsg)) {
            notify("The collection was not added due to the following : " + errorThrown, "error");
        }
    });

    return false;
}

function removeCollectionFromSpace(spaceId, id, event){
    var request = jsRoutes.api.Spaces.removeCollection(spaceId, id).ajax({
        type: 'POST'
    });

    request.done(function (response, textStatus, jqXHR){
        $('#col_'+spaceId).remove();
        if(!isSharingEnabled && $('#spacesList .row').length == 0){
            $("#add-to-space-widget").removeClass("hidden");
        }

    });

    request.fail(function (jqXHR, textStatus, errorThrown){
        console.error("The following error occured: " + textStatus, errorThrown);
        var errMsg = "You must be logged in to remove a collection.";
        if (!checkErrorAndRedirect(jqXHR, errMsg)) {
            notify("The collection was not removed due to : " + errorThrown, "error");
        }
    });
    return false;
}

//Method to remove the collection from space and redirect back to a specific URL on completion
function removeCollectionFromSpaceAndRedirect(spaceId, collectionId, isreload, url) {
    console.log(url);
    var removeDatasets =$('#removedatasets').is(':checked');
    var request = jsRoutes.api.Spaces.removeCollection(spaceId, collectionId, removeDatasets).ajax({
        type: 'POST'
    });

    request.done(function (response, textStatus, jqXHR){
        if(isreload === "true")
            if(url === undefined) {
                reloadPage = "/spaces";
            } else {
                window.location.href=url;
            }
        else {
            $('#col_' + spaceId).remove();
            if(!isSharingEnabled && $('#spacesList .row').length == 0 ) {
                $("#add-to-space-widget").removeClass("hidden");
            }
        }
    });

    request.fail(function (jqXHR, textStatus, errorThrown){
        console.error("The following error occured: " + textStatus, errorThrown);
        var errMsg = "You must be logged in to remove a collection.";
        if (!checkErrorAndRedirect(jqXHR, errMsg)) {
            notify("The collection was not removed due to : " + errorThrown, "error");
        }
    });
}

function addDatasetToSpace(id, spaceTitle) {
    var selectedId = $("#spaceAddSelect").val();
    if (!selectedId) return false;
    var selectedName = $("#spaceAddSelect option:selected").text();
    selectedName = selectedName.replace(/\n/g, "<br>");

    var request = jsRoutes.api.Spaces.addDatasetToSpace(selectedId, id).ajax({
        type: 'POST'
    });

    request.done(function (response, textStatus, jqXHR) {
        var o = $.parseJSON(jqXHR.responseText);
        var txt = '<div id="col_'+selectedId+'" class="row bottom-padding">' +
            '<div class="col-md-2"><a href="'+jsRoutes.controllers.Spaces.getSpace(selectedId).url+'" id='+selectedId+'>' +
            '<span class="smallicon glyphicon glyphicon-hdd"></span></a></div>' +
            '<div class="col-md-10">' +
            '<div><a href="'+jsRoutes.controllers.Spaces.getSpace(selectedId).url+'" id='+selectedId+' class ="space">'+selectedName+'</a>' +
            '</div>';
        txt = txt + '<div>';
        if (o.datasetsInSpace == 1) {
            txt = txt + o.datasetsInSpace +' dataset';
        } else {
            txt = txt + o.datasetsInSpace +' datasets';
        }
        txt = txt + ' | <button class="btn btn-link btn-xs" onclick="confirmRemoveResourceFromResourceEvent(\'space\',\''+spaceTitle+'\',\''+selectedId+'\',\'dataset\',\''+id+'\', event)" title="Remove the dataset from the space">' +
            '<span class="glyphicon glyphicon-remove"></span> Remove</button>';
        txt = txt + '</div>';
        txt = txt + '</div>';
        txt = txt + '</div>';
        $("#spacesList").append(txt);
        $("#spaceAddSelect").select2("val", "");
        if(!isSharingEnabled) {
            $("#add-to-space-widget").addClass("hidden");
            $('#dataset-users').removeClass("hidden");
        }
        $('#publish').removeClass('disabled');

    });

    request.fail(function (jqXHR, textStatus, errorThrown){
        console.error("The following error occured: " + textStatus, errorThrown);
        var errMsg = "You must be logged in to add a dataset.";
        if (!checkErrorAndRedirect(jqXHR, errMsg)) {
            notify("The dataset was not added due to the following : " + errorThrown, "error");
        }
    });

    return false;
}

function copyDatasetToSpace(id) {
    var selectedId = $('#spaceCopySelect').val();
    if(!selectedId) return false;
    var request = jsRoutes.api.Datasets.copyDatasetToSpace(id, selectedId).ajax({
        type: 'POST'
    });
    request.done(function(response, textStatus, jqXHR){
      location.href = jsRoutes.controllers.Datasets.dataset(response.newDatasetId).url;
    });
    request.fail(function(jqXHR, textStatus, errorThrown) {
        notify("The dataset was not copied to the space due to the following: " + errorThrown, "error");
    });
}

function removeDatasetFromSpace(spaceId, datasetId, event){
    var request = jsRoutes.api.Spaces.removeDataset(spaceId, datasetId).ajax({
        type: 'POST'
    });

    request.done(function (response, textStatus, jqXHR){
        $('#col_'+spaceId).remove();
        if(!isSharingEnabled &&  $('#spacesList .row').length == 0) {
            $("#add-to-space-widget").removeClass("hidden");
            $('#dataset-users').addClass("hidden");
        }
        if($('#spacesList .row').length == 0) {
        	$('#publish').addClass('disabled');
       	}

    });

    request.fail(function (jqXHR, textStatus, errorThrown){
        console.error("The following error occured: " + textStatus, errorThrown);
        var errMsg = "You must be logged in to remove a dataset.";
        if (!checkErrorAndRedirect(jqXHR, errMsg)) {
            notify("The dataset was not removed due to : " + errorThrown, "error");
        }
    });
    return false;
}

//Method to remove the dataset from space and redirect back to a specific URL on completion
function removeDatasetFromSpaceAndRedirect(spaceId, datasetId, isreload, url){
    var request = jsRoutes.api.Spaces.removeDataset(spaceId, datasetId).ajax({
        type: 'POST'
    });

    request.done(function (response, textStatus, jqXHR){
        if(isreload === "true")
            if(url === undefined) {
                reloadPage = "/spaces";
            } else {
                window.location.href=url;
            }
        else {
            $('#col_' + spaceId).remove();
            if(!isSharingEnabled && $('#spacesList .row').length == 0) {
                $("#add-to-space-widget").removeClass("hidden");
                $('#dataset-users').addClass("hidden");
            }
            if($('#spacesList .row').length == 0) {
            	$('#publish').addClass('disabled');
           	}
            if(response.isTrial.valueOf() ==="true"){
                $('#access').hide();
            }
        }

    });
    request.fail(function (jqXHR, textStatus, errorThrown){
        console.error("The following error occured: " + textStatus, errorThrown);
        var errMsg = "You must be logged in to remove a dataset.";
        if (!checkErrorAndRedirect(jqXHR, errMsg)) {
            notify("The dataset was not removed due to : " + errorThrown, "error");
        }
    });
}

function updateSpaceEditLink(space_id, space_name) {
    $('#space_link').attr("href", jsRoutes.controllers.Spaces.getSpace(space_id).url).text(space_name);
}

function updateUsersInSpace(spaceId) {
    //Generate the string for each level
    var currRole = null;
    var roleUserMap = {};
    for (var i = 0; i < roleArray.length; i++) {
        currRole = roleArray[i].replace(/ /g, '');
        //console.log("roleList[i] is " + roleArray[i] + " and " + currRole);

        var idsCurrent = $('#' + currRole + '-current li a').map(function(){ return this.id }).get().join(',');
        //console.log('idsCurrent are ' + idsCurrent);
        var idsSelected = $("#" + currRole + " option:selected").map(function(){ return this.value }).get().join(",");
        //console.log("idsSelected are " + idsSelected);

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
    var request = jsRoutes.api.Spaces.updateUsers(spaceId).ajax({
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
        var errMsg = "You must be logged in to update the users.";
        notify("Failed to update users due to:" + errorThrown, "error");

    });

    return false;
}

function acceptSpaceRequest(spaceId, userId, userName){
    var role = $("#roleSelect-"+userId).val();
    var request = jsRoutes.api.Spaces.acceptRequest(spaceId, userId, role).ajax({
        type : 'POST'
    });
    request.done ( function ( response, textStatus, jqXHR ) {
        $("#request-tr-"+userId).remove();
        var sd=$('#request-counter').text();
        sd=parseInt(sd.split('(')[1]) -1;
        $('#request-counter').text("Requests ("+ sd +")");
        var addUser ='<li><a href= "'+jsRoutes.controllers.Profile.viewProfileUUID(userId).url+'" id="'+userId+'">'
                      + userName + '</a>'
        + '<a class="remove-user" id="'+ userId +'"><span class="glyphicon glyphicon-remove"></span></a></li>';
        $('#'+role+'-current').append(addUser);
        $("option[value='"+userId+"']").each(function() {
            $(this).remove();
        });
        $( ".chosen-select" ).trigger("chosen:updated");
        console.log("Successful accept request");
    });
    request.fail(function(jqXHR, textStatus, errorThrown) {
        console.error("The following error occured: " + textStatus, errorThrown);
        var errMsg = "You must be logged in to accept request.";
        if (!checkErrorAndRedirect(jqXHR, errMsg)) {
            notify("Accept request failed due to : " + errorThrown, "error");
        }
    });
    return false;
}

function rejectSpaceRequest(id, user){
    var request = jsRoutes.api.Spaces.rejectRequest(id, user).ajax({
        type : 'POST'
    });
    request.done ( function ( response, textStatus, jqXHR ) {
        $("#request-tr-"+user).remove();
        var sd=$('#request-counter').text();
        sd=parseInt(sd.split('(')[1]) -1;
        $('#request-counter').text("Requests ("+ sd +")");
        console.log("Successful reject request");
    });
    request.fail(function(jqXHR, textStatus, errorThrown) {
        console.error("The following error occured: " + textStatus, errorThrown);
        var errMsg = "You must be logged in to reject request.";
        if (!checkErrorAndRedirect(jqXHR, errMsg)) {
            notify("Reject request failed due to : " + errorThrown, "error");
        }
    });
    return false;
}
