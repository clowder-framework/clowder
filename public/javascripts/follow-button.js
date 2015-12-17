function followCallback() {
    var $followerSize = $('#followerSize');
    var followerSize = parseInt($followerSize.text());
    $followerSize.text(followerSize + 1);
}

function unfollowCallback() {
    var $followerSize = $('#followerSize');
    var followerSize = parseInt($followerSize.text());
    $followerSize.text(followerSize - 1);
}


$(document).ready(function() {
	var followButtonList = $("[id=followButton]");
	
	followButtonList.click(function() {
		var index = followButtonList.index(this);
		var followButton = followButtonList.eq(index);
		console.log(followButton);
		jsObjectId = followButton.attr("objectid");
		jsObjectType = followButton.attr("objecttype");
		
	if (jsObjectType === "user") {
		if (followButton.text( ).trim() === "Follow") {
    		jsRoutes.api.Users.follow(jsObjectId).ajax({
    	        type: "POST"
    	      })
    	        .done(function(data) {
    	          followButton.html("<span class='glyphicon glyphicon-star-empty'></span> Unfollow");
    	          followButton.removeClass('btn-link');
    	          followButton.addClass('btn-link');
    	          followCallback();
    	        })
    	        .fail(function(data) {
    	          console.log(data);
    	          console.log("Failed to follow");
    	        });
    	    } else {
    	      jsRoutes.api.Users.unfollow(jsObjectId).ajax({
    	        type: "POST"
    	      })
    	        .done(function(data) {
    	          followButton.html("<span class='glyphicon glyphicon-star'></span> Follow");
    	          followButton.removeClass('btn-link');
    	          followButton.addClass('btn-link');
				  if(removeIndicator) {
					  $('#'+followButton.attr('objectid')+'-tile').remove();
					  $('#'+followButton.attr('objectid')+'-listitem').remove();
					  activate();
				  }
    	          unfollowCallback();
    	        })
    	        .fail(function(data) {
    	          console.log(data);
    	          console.log("Failed to unfollow");
    	        });
    	    }
	}
	else if(jsObjectType === "file") {
		if (followButton.text( ).trim() === "Follow") {
    		jsRoutes.api.Files.follow(jsObjectId).ajax({
    	        type: "POST"
    	      })
    	        .done(function(data) {
    	          followButton.html("<span class='glyphicon glyphicon-star-empty'></span> Unfollow");
    	          followButton.removeClass('btn-link');
    	          followButton.addClass('btn-link');
    	          followCallback();
    	        })
    	        .fail(function(data) {
    	          console.log(data);
    	          console.log("Failed to follow");
    	        });
    	    } else {
    	      jsRoutes.api.Files.unfollow(jsObjectId).ajax({
    	        type: "POST"
    	      })
    	        .done(function(data) {
    	          followButton.html("<span class='glyphicon glyphicon-star'></span> Follow");
    	          followButton.removeClass('btn-link');
    	          followButton.addClass('btn-link');
				  if(removeIndicator) {
					  $('#'+followButton.attr('objectid')+'-tile').remove();
					  $('#'+followButton.attr('objectid')+'-listitem').remove();
					  activate();
				  }
    	          unfollowCallback();
    	        })
    	        .fail(function(data) {
    	          console.log(data);
    	          console.log("Failed to unfollow");
    	        });
    	    }
    } else if(jsObjectType === "dataset") {
    	if (followButton.text( ).trim() === "Follow") {
    		jsRoutes.api.Datasets.follow(jsObjectId).ajax({
    	        type: "POST"
    	      })
    	        .done(function(data) {
    	          followButton.html("<span class='glyphicon glyphicon-star-empty'></span> Unfollow");
    	          followButton.removeClass('btn-link');
    	          followButton.addClass('btn-link');
    	          followCallback();
    	        })
    	        .fail(function(data) {
    	          console.log(data);
    	          console.log("Failed to follow");
    	        });
    	    } else {
    	      jsRoutes.api.Datasets.unfollow(jsObjectId).ajax({
    	        type: "POST"
    	      })
    	        .done(function(data) {
    	          followButton.html("<span class='glyphicon glyphicon-star'></span> Follow");
    	          followButton.removeClass('btn-link');
    	          followButton.addClass('btn-link');
				  if(removeIndicator) {
					  $('#'+followButton.attr('objectid')+'-tile').remove();
					  $('#'+followButton.attr('objectid')+'-listitem').remove();
					  activate();
				  }
    	          unfollowCallback();
    	        })
    	        .fail(function(data) {
    	          console.log(data);
    	          console.log("Failed to unfollow");
    	        });
    	    }
    } else if(jsObjectType === "collection" ) {
    	if (followButton.text( ).trim() === "Follow") {
    		jsRoutes.api.Collections.follow(jsObjectId).ajax({
    	        type: "POST"
    	      })
    	        .done(function(data) {
    	          followButton.html("<span class='glyphicon glyphicon-star-empty'></span> Unfollow");
    	          followButton.removeClass('btn-link');
    	          followButton.addClass('btn-link');
    	          followCallback();
    	        })
    	        .fail(function(data) {
    	          console.log(data);
    	          console.log("Failed to follow");
    	        });
    	    } else {
    	      jsRoutes.api.Collections.unfollow(jsObjectId).ajax({
    	        type: "POST"
    	      })
    	        .done(function(data) {
    	          followButton.html("<span class='glyphicon glyphicon-star'></span> Follow");
    	          followButton.removeClass('btn-link');
    	          followButton.addClass('btn-link');
				  if(removeIndicator) {
					  $('#'+followButton.attr('objectid')+'-tile').remove();
					  $('#'+followButton.attr('objectid')+'-listitem').remove();
					  activate();
				  }
    	          unfollowCallback();
    	        })
    	        .fail(function(data) {
    	          console.log(data);
    	          console.log("Failed to unfollow");
    	        });
    	    }
	} else if(jsObjectType === "space" ) {
		if (followButton.text( ).trim() === "Follow") {
			jsRoutes.api.Spaces.follow(jsObjectId).ajax({
				type: "POST"
			})
				.done(function(data) {
					followButton.html("<span class='glyphicon glyphicon-star-empty'></span> Unfollow");
					followButton.removeClass('btn-link');
					followButton.addClass('btn-link');
					followCallback();
				})
				.fail(function(data) {
					console.log(data);
					console.log("Failed to follow");
				});
		} else {
			jsRoutes.api.Spaces.unfollow(jsObjectId).ajax({
				type: "POST"
			})
				.done(function(data) {
					followButton.html("<span class='glyphicon glyphicon-star'></span> Follow");
					followButton.removeClass('btn-link');
					followButton.addClass('btn-link');
					if(removeIndicator) {
						$('#'+followButton.attr('objectid')+'-tile').remove();
						$('#'+followButton.attr('objectid')+'-listitem').remove();
						activate();
					}
					unfollowCallback();
				})
				.fail(function(data) {
					console.log(data);
					console.log("Failed to unfollow");
				});
		}
	}
  });
});