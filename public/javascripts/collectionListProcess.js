function removeCollection(collectionId,event){
	var request = $.ajax({
	       type: 'POST',
	       url: "http://"+hostIp+":"+window.location.port+"/api/collections/"+collectionId+"/remove"
	     });
	request.done(function (response, textStatus, jqXHR){
        console.log("Response " + response);
        $(event.target.parentNode.parentNode).remove();    
    });
	request.fail(function (jqXHR, textStatus, errorThrown){
		console.error(
    		"The following error occured: "+
    		textStatus, errorThrown		            
			);
		alert("ERROR: " + errorThrown +". Collection not removed. Maybe it was already removed." );
			});
}