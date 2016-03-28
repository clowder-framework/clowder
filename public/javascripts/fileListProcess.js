function removeFile(fileId,event, reloadPage){
	if(reloadPage === undefined) reloadPage = false;

	var request = jsRoutes.api.Files.removeFile(fileId).ajax({
		type: 'DELETE'
	});

	request.done(function (response, textStatus, jqXHR){
		$(event.target.parentNode.parentNode).remove();

		if(reloadPage == true)
			location.reload(true);
	});
	
	request.fail(function (jqXHR, textStatus, errorThrown){
        console.error("The following error occured: " + textStatus, errorThrown);
        var errMsg = "You must be logged in to delete a file from the system.";        
        if (!checkErrorAndRedirect(jqXHR, errMsg)) {
            notify("The file was not deleted from the system due to : " + errorThrown, "error");
        }
	});	
}

function removeFileAndRedirect(fileId, url){
	if(url === undefined) reloadPage = "/files";

	var request = jsRoutes.api.Files.removeFile(fileId).ajax({
		type: 'DELETE'
	});

	request.done(function (response, textStatus, jqXHR){		
		window.location.href=url;
		$(window).trigger('fileDelete');
	});
	
	request.fail(function (jqXHR, textStatus, errorThrown){
        console.error("The following error occured: " + textStatus, errorThrown);
        var errMsg = "You must be logged in to delete a file from the system.";        
        if (!checkErrorAndRedirect(jqXHR, errMsg)) {
            notify("The file was not deleted from the system due to : " + errorThrown, "error");
        }
	});	
}