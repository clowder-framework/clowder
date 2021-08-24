function removeCollection(id, isreload, newurl, resourceFromType){
	var url = jsRoutes.api.Collections.removeCollection(id);
	if(resourceFromType == 'space'){
		var removeDatasets = $('#removedatasets').val();
		url = jsRoutes.api.Collections.removeCollection(id , removeDatasets)
	}
	var request = url.ajax({
		type: 'DELETE'
	});
	request.done(function (response, textStatus, jqXHR){
		if(isreload === "true")
			window.location.href=newurl;
		else {
			$('#'+ id+'-listitem').remove();
			var obj = $('#'+ id+'-tile');
			if($('#masonry').length > 0) {
				$('#masonry').masonry('remove', obj);
				$('#masonry').masonry('layout');
			}
			if($('#masonry-collections').length > 0) {
				$('#masonry-collections').masonry('remove', obj);
				$('#masonry-collections').masonry('layout');
			}
		}
    });
	request.fail(function (jqXHR, textStatus, errorThrown){
		console.error("The following error occurred: "+textStatus, errorThrown);
        var errMsg = "You must be logged in to remove a collection from the system.";
        if (!checkErrorAndRedirect(jqXHR, errMsg)) {
            notify("The collection was not removed due to : " + errorThrown, "error");
        }   		
	});
}

//Method to remove the collection and redirect back to a specific URL on completion
function removeCollectionAndRedirect(collectionId, url){
	if(url === undefined) reloadPage = "/collections";
	
	var request = jsRoutes.api.Collections.removeCollection(collectionId).ajax({
		type: 'DELETE'
	});

	request.done(function (response, textStatus, jqXHR){
		console.log("Response " + response);		
		window.location.href=url;
	});
	
	request.fail(function (jqXHR, textStatus, errorThrown){
        console.error("The following error occurred: " + textStatus, errorThrown);
        var errMsg = "You must be logged in to delete a collection from the system.";        
        if (!checkErrorAndRedirect(jqXHR, errMsg)) {
            notify("The collection was not deleted from the system due to : " + errorThrown, "error");
        }
	});	
}

//method to remove child from parent and redirect to url
function removeChildCollectionFromParent(parentId, childId, url) {
	if (url == undefined) reloadPage = "/collections";

	var request = jsRoutes.api.Collections.removeSubCollection(parentId, childId).ajax({
		type : 'POST'
	});

	request.done(function (response,textStatus,jqXHR){
		console.log("Response " + response);
		window.location.href=url;
	});

	request.fail(function (jqXHR, textStatus, errorThrown){
		console.error("The following error occurred: " + textStatus, errorThrown);
		var errMsg = "You must be logged in to remove a subcollection from the system.";
		if (!checkErrorAndRedirect(jqXHR, errMsg)) {
			notify("The child collection was not removed from the system due to : " + errorThrown, "error");
		}
	});
}

function restoreCollection(id,isreload,newurl){
    var url = jsRoutes.api.Collections.restoreCollection(id)
    var request = url.ajax({
        type: 'PUT'
    });
    request.done(function (response, textStatus, jqXHR){
        window.location.href=newurl;

    });
    request.fail(function (jqXHR, textStatus, errorThrown){
        console.error("The following error occurred: "+textStatus, errorThrown);
        var errMsg = "You must be logged in to remove a collection from the system.";
        if (!checkErrorAndRedirect(jqXHR, errMsg)) {
            notify("The collection was not restored due to : " + errorThrown, "error");
        }
    });
}