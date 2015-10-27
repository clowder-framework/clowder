//@author: Jong Lee (jonglee)

//Method to remove the space and redirect back to a specific URL on completion
function removeSpaceAndRedirect(spaceId, url){
	if(url === undefined) url = "/spaces";
	
	var request = jsRoutes.api.Spaces.removeSpace(spaceId).ajax({
		type: 'DELETE'
	});

	request.done(function (response, textStatus, jqXHR){
		console.log("Response " + response);		
		window.location.href=url;
	});
	
	request.fail(function (jqXHR, textStatus, errorThrown){
        console.error("The following error occurred: " + textStatus, errorThrown);
        var errMsg = "You must be logged in to delete a space from the system.";
        if (!checkErrorAndRedirect(jqXHR, errMsg)) {
            alert("The space was not deleted from the system due to : " + errorThrown);
        }
	});	
}