/**
 * Created by lmarini on 2/5/15.
 */
resource_type_enum = {
    DATASET : 0,
    COLLECTION : 1
}


function changeSpace(resource_id, resource_type) {

    var request = jsRoutes.api.Spaces.list().ajax({
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

function updateUsersInSpace(spaceId, url) {
	//Hide the modal
	$('#modalUser').modal('hide');
	
	//Generate the string for each level
	var roleUserMap = {};
	for (var i = 0; i < roleArray.length; i++) {
		console.log("roleList[i] is " + roleArray[i] + " and " + roleArray[i].replace(/ /g, ''));
		var ids = $("#" + roleArray[i].replace(/ /g, '') + " option:selected").map(function(){ return this.value }).get().join(",");
		console.log("ids are " + ids); 
		roleUserMap[roleArray[i]] = ids;
	}		
	
	var jsonData = JSON.stringify({"rolesandusers":roleUserMap});
	var request = jsRoutes.api.Spaces.updateUsers(spaceId).ajax({
        data: jsonData,
        type: 'POST',
        contentType: "application/json",
    });
	                        	                        
    request.done(function (response, textStatus, jqXHR){	    
    	//Successful attachment of multiple files
        console.log("Successful response from updateUsers. URL is " + url);
        window.location.href = url;
    });


    request.fail(function (jqXHR, textStatus, errorThrown){
        console.error("The following error occured: " + textStatus, errorThrown);
        var errMsg = "You must be logged in to update the users contained within a space.";                                
        if (!checkErrorAndRedirect(jqXHR, errMsg)) {
        	console.error("Unhandled error.");
        }  
    });
	
    
    return false;
}

window['changeSpace'] = changeSpace;