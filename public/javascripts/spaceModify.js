
resource_type_enum = {
    DATASET : 0,
    COLLECTION : 1
}


function addCollectionToSpace(id) {
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
            '<div class="col-md-2"></div>' +
            '<div class="col-md-10"><div><a href="'+jsRoutes.controllers.Spaces.getSpace(selectedId).url+'" id='+selectedId+' class ="space">'+selectedName+'</a></div>' +
            '<div>';
        if (o.collectionInSpace == 1) {
            txt = txt + o.collectionInSpace +' collection';
        } else {
            txt = txt + o.collectionInSpace +' collections';
        }
        txt = txt + ' | <a href="#" class="btn btn-link btn-xs" onclick="removeCollectionFromSpace(\''+selectedId+'\', \''+id+'\', event)" title="Remove from space">' +
            '<span class="glyphicon glyphicon-remove"></span> Remove</a></div></div></div>';
        $("#spacesList").append(txt);
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


function removeCollectionFromSpace(spaceId, id, event){

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


function addDatasetToSpace(id) {
    var selectedId = $("#spaceAddSelect").val();
    if (!selectedId) return false;
    var selectedName = $("#spaceAddSelect option:selected").text();
    selectedName = selectedName.replace(/\n/g, "<br>");

    var request = jsRoutes.api.Spaces.addDatasetToSpace(selectedId, id).ajax({
        type: 'POST'
    });

    request.done(function (response, textStatus, jqXHR) {
        var o =$.parseJSON(jqXHR.responseText);
        var txt = '<div id="col_'+selectedId+'" class="row bottom-padding">' +
            '<div class="col-md-2"></div>' +
            '<div class="col-md-10"><div><a href="'+jsRoutes.controllers.Spaces.getSpace(selectedId).url+'" id='+selectedId+' class ="space">'+selectedName+'</a></div>' +
            '<div>';
        if (o.datasetsInSpace == 1) {
            txt = txt + o.datasetsInSpace +' dataset';
        } else {
            txt = txt + o.datasetsInSpace +' datasets';
        }
        txt = txt + ' | <a href="#" class="btn btn-link btn-xs" onclick="removeDatasetFromSpace(\''+selectedId+'\', \''+id+'\', event)" title="Remove from space">' +
            '<span class="glyphicon glyphicon-remove"></span> Remove</a></div></div></div>';
        $("#spacesList").append(txt);
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


function removeDatasetFromSpace(spaceId, id, event){

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


function updateSpaceEditLink(space_id, space_name) {
    $('#space_link').attr("href", jsRoutes.controllers.Spaces.getSpace(space_id).url).text(space_name);
}


function updateUsersInSpace(spaceId) {
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
        var errMsg = "You must be logged in to update the users contained within a space.";

    });

    return false;
}

function acceptSpaceRequest(spaceId, userId, userName){
    var role = $("#roleSelect").val();
    var request = jsRoutes.controllers.Spaces.acceptRequest(spaceId, userId, role).ajax({
        type : 'GET',
        contentType : "application/json"
    });
    request.done ( function ( response, textStatus, jqXHR ) {
        $("#request-tr-"+userId).hide();
        var sd=$('#request-counter').text();
        console.log(sd);
        sd=parseInt(sd.split('(')[1]) -1;
        $('#request-counter').text("Requests ("+ sd +")");
        var addUesr ='<li><a href= "'+jsRoutes.controllers.Profile.viewProfileUUID(userId).url+'" id="'+userId+'">'
                      + userName + '</a>'
        + '<a class="remove-user" id="'+ userId +'"><span class="glyphicon glyphicon-remove"></span></a></li>';
        $('#'+role+'-current').append(addUesr);
        console.log("Successful accept request");
    });
    request.fail(function(jqXHR, textStatus, errorThrown) {
        console.error("The following error occured: " + textStatus, errorThrown);
        var errMsg = "You must be logged in to accept request.";
    });
    return false;
}

function rejectSpaceRequest(id, user){
    var request = jsRoutes.controllers.Spaces.rejectRequest(id, user).ajax({
        type : 'GET',
        contentType : "application/json"
    });
    request.done ( function ( response, textStatus, jqXHR ) {
        $("#request-tr-"+user).hide();
        var sd=$('#request-counter').text();
        console.log(sd);
        sd=parseInt(sd.split('(')[1]) -1;
        $('#request-counter').text("Requests ("+ sd +")");
        console.log("Successful reject request");
    });
    request.fail(function(jqXHR, textStatus, errorThrown) {
        console.error("The following error occured: " + textStatus, errorThrown);
        var errMsg = "You must be logged in to reject request.";
    });
    return false;
}
