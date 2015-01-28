//@author: Jong Lee (jonglee)
//TODO: the code needs to be refactored based on the api implementation

//Method to remove the space
function removeSpace(spaceId,event){
	var request = jsRoutes.api.Spaces.removeSpace(spaceId).ajax({
		type: 'POST'
	});
	request.done(function (response, textStatus, jqXHR){
        console.log("Response " + response);
        if($(event.target).is("span")){
        	$(event.target.parentNode.parentNode.parentNode).remove();
        }
        else{
        	$(event.target.parentNode.parentNode).remove();
        }    
    });
	request.fail(function (jqXHR, textStatus, errorThrown){
		console.error("The following error occured: "+textStatus, errorThrown);
        var errMsg = "You must be logged in to remove a collection from the system.";
        if (!checkErrorAndRedirect(jqXHR, errMsg)) {
            alert("The collection was not removed due to : " + errorThrown);
        }   		
	});
}

//Method to remove the space and redirect back to a specific URL on completion
function removeSpaceAndRedirect(spaceId, url){
	if(url === undefined) reloadPage = "/collections";
	
	var request = jsRoutes.api.Spaces.removeSpace(spaceId).ajax({
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
            alert("The collection was not deleted from the system due to : " + errorThrown);
        }
	});	
}