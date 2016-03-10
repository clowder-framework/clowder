//@author: Jong Lee (jonglee)

//Method to remove the space and redirect back to a specific URL on completion
function removeSpace(spaceId, isreload, url){
	
	var request = jsRoutes.api.Spaces.removeSpace(spaceId).ajax({
		type: 'DELETE'
	});

	request.done(function (response, textStatus, jqXHR){
		if(isreload === "true")
			window.location.href=url;
		else {
			$('#'+ spaceId+'-listitem').remove();
			var obj = $('#'+ spaceId+'-tile');
			if($('#masonry').length > 0) {
				$('#masonry').masonry('remove', obj);
				$('#masonry').masonry('layout');
			}
			if($('#masonry-spaces').length > 0) {
				$('#masonry-spaces').masonry('remove', obj);
				$('#masonry-spaces').masonry('layout');
			}
		}
	});
	
	request.fail(function (jqXHR, textStatus, errorThrown){
        console.error("The following error occurred: " + textStatus, errorThrown);
        var errMsg = "You must be logged in to delete a space from the system.";
        if (!checkErrorAndRedirect(jqXHR, errMsg)) {
            alert("The space was not deleted from the system due to : " + errorThrown);
        }
	});	
}