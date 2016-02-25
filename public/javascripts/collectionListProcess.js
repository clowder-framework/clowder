function removeCollection(collectionId, isreload, url){
	var request = jsRoutes.api.Collections.removeCollection(collectionId).ajax({
		type: 'DELETE'
	});
	request.done(function (response, textStatus, jqXHR){
		if(isreload == true)
			window.location.href=url;
		else {
			$('#'+ collectionId+'-tile').remove();
			$('#'+ collectionId+'-listitem').remove();

		}
    });
	request.fail(function (jqXHR, textStatus, errorThrown){
		console.error("The following error occured: "+textStatus, errorThrown);
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
        console.error("The following error occured: " + textStatus, errorThrown);
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
		console.error("The following error occured: " + textStatus, errorThrown);
		var errMsg = "You must be logged in to remove a subcollection from the system.";
		if (!checkErrorAndRedirect(jqXHR, errMsg)) {
			notify("The child collection was not removed from the system due to : " + errorThrown, "error");
		}
	});
}