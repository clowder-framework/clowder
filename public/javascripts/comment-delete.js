//Functions to delete comments
//
//Referenced by comment.scala.html
//
function deleteComment(commentId, reloadPage){
	
	if(reloadPage === undefined) reloadPage = false;
	
	var jsonData = JSON.stringify({"id":commentId});	
    
    var request = jsRoutes.api.Comments.removeComment(commentId).ajax({
  	    data: "{}",
        type: 'DELETE',
        contentType: "application/json",
    });
    
    request.done(function (response, textStatus, jqXHR){
        //Sucessful update of the DB - update the interface             	
        deleteCommentCallback(commentId);
    });

     
     request.fail(function (jqXHR, textStatus, errorThrown){
    	 console.error("The following error occured: " + textStatus, errorThrown);
         var errMsg = "You must be logged in to delete a comment.";                                
         if (!checkErrorAndRedirect(jqXHR, errMsg)) {
         	notify("The comment was not deleted due to : " + errorThrown, "error");
         }
         else {
        	 var responseText = jqXHR.responseText;
        	 notify(responseText, "error");
         }
     });
    
	 return false;	
}