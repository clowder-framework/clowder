
resource_type_enum = {
    DATASET : 0,
    COLLECTION : 1
}


function changeSpace(resource_id, resource_type) {

    var request = jsRoutes.api.Spaces.listSpacesCanAdd().ajax({
        type: 'GET'
    });

    request.done(function (response, textStatus, jqXHR){
        showModal(response, resource_id, resource_type);
    });

    request.fail(function (jqXHR, textStatus, errorThrown){
        console.error("The following error occured: " + textStatus, errorThrown);
    });
}

function showModal(spaces, resource_id, resource_type) {
    var modalTemplate = Handlebars.getTemplate('/assets/templates/spaces/assign');
    var html = modalTemplate({spaces: spaces});
    $('.container').append(html);
    $('#spaces-assign').modal();

    $('.list-group li').click(function(e) {
        e.preventDefault();
        $that = $(this);
        $that.parent().find('li').removeClass('active');
        $that.addClass('active');
    });

    $('#spaces_add').click(function() {
        var space_id = $('.list-group-item.active').data("space-id");
        var space_name = $('.list-group-item.active').data("space-name");
        if(resource_type == resource_type_enum.DATASET) {
            addDatasetToSpace(resource_id, space_id, space_name);
        } else if(resource_type == resource_type_enum.COLLECTION) {
            addCollectionToSpace(resource_id, space_id, space_name);
        } else {
            console.error("Resource type not recognized when adding to space");
        }

    });
}

function addCollectionToSpace(collection_id, space_id, space_name) {

    var request = jsRoutes.api.Spaces.addCollection(space_id).ajax({
        type: 'POST',
        data: JSON.stringify({'collection_id': collection_id}),
        dataType: "json",
        contentType: "application/json; charset=utf-8"
    });

    request.done(function (response, textStatus, jqXHR){
        console.log('Collection ' + collection_id + ' added to space ' + space_id);
        $('#spaces-assign').modal('hide');
        updateSpaceEditLink(space_id, space_name);
    });

    request.fail(function (jqXHR, textStatus, errorThrown){
        console.error("The following error occured: " + textStatus, errorThrown);
    });
}

function addDatasetToSpace(dataset_id, space_id, space_name) {

    var request = jsRoutes.api.Spaces.addDataset(space_id).ajax({
        type: 'POST',
        data: JSON.stringify({'dataset_id': dataset_id}),
        dataType: "json",
        contentType: "application/json; charset=utf-8"
    });

    request.done(function (response, textStatus, jqXHR){
        console.log('Dataset ' + dataset_id + ' added to space ' + space_id);
        $('#spaces-assign').modal('hide');
        updateSpaceEditLink(space_id, space_name);
    });

    request.fail(function (jqXHR, textStatus, errorThrown){
        console.error("The following error occured: " + textStatus, errorThrown);
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

function acceptSpaceRequest(id, user){
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
    return false;
}

function rejectSpaceRequest(id, user){
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
    return false;
}

window['changeSpace'] = changeSpace;