function removeFile(fileId, isreload, url){

	var request = jsRoutes.api.Files.removeFile(fileId).ajax({
		type: 'DELETE'
	});

	request.done(function (response, textStatus, jqXHR){
		if(isreload === "true")
			window.location.href=url;
		else {
			var obj = $('#'+ fileId+'-tile');
			$('#masonry').masonry( 'remove', obj );
			$('#masonry').masonry( 'layout' );
			$('#'+ fileId+'-listitem').remove();

			// After deleting the last file from a folder, display the empty message
			if($("#tile-view").find("div[id$='-listitem']").length == 0){
				$('#empty-folder-div').show();
			}
		}
	});
	
	request.fail(function (jqXHR, textStatus, errorThrown){
        console.error("The following error occurred: " + textStatus, errorThrown);
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
        console.error("The following error occurred: " + textStatus, errorThrown);
        var errMsg = "You must be logged in to delete a file from the system.";        
        if (!checkErrorAndRedirect(jqXHR, errMsg)) {
            notify("The file was not deleted from the system due to : " + errorThrown, "error");
        }
	});	
}