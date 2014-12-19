function removeFile(fileId,event, reloadPage){
	if(reloadPage === undefined) reloadPage = false;

	var request = jsRoutes.api.Files.removeFile(fileId).ajax({
		type: 'POST'
	});

	request.done(function (response, textStatus, jqXHR){
		console.log("Response " + response);
		$(event.target.parentNode.parentNode).remove();

		if(reloadPage == true)
			location.reload(true);
	});
	
	request.fail(function (jqXHR, textStatus, errorThrown){
        console.error("The following error occured: " + textStatus, errorThrown);
        var errMsg = "You must be logged in to delete a file from the system.";        
        if (!checkErrorAndRedirect(jqXHR, errMsg)) {
            alert("The file was not deleted from the system due to : " + errorThrown);
        }
	});	
}