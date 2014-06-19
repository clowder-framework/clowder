function deleteComment(commentId, reloadPage){
	
	if(reloadPage === undefined) reloadPage = false;
	
	var jsonData = JSON.stringify({"id":commentId});	
    
    var request = jsRoutes.api.Comments.removeComment(commentId).ajax({
  	    data: "{}",
        type: 'DELETE',
        contentType: "application/json",
    });
    
    request.done(function (response, textStatus, jqXHR){
        ///console.log("Response " + response);
        //Sucessful update of the DB - update the interface             	
        deleteCommentCallback(commentId);
    });

     
     request.fail(function (jqXHR, textStatus, errorThrown){
        console.error(
            "The following error occured: "+
            textStatus, errorThrown            
        );
        alert("Deleting the comment failed. " + textStatus + " and " + errorThrown);
     });
    
	 return false;	
}