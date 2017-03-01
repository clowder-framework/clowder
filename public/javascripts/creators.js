function addCreator() {

	$('<div class="add_creator"> </div>').insertAfter($('#ds_creators'));
	
	//simple means there is no service to query for people name/email/ids and the input should be a simple text box
	if ($('#add-creator').hasClass("simple")) {
		$('.add_creator')
				.append(
						'<input type="text" id="creatorAddSelect" class="form-control add-resource" tabindex="-1" aria-hidden="true">');

	} else {
		$('.add_creator')
				.append(
						'<select id="creatorAddSelect" class="form-control add-resource select2" tabindex="-1" aria-hidden="true">');
	}
	$('.add_creator')
			.append(
					'<button id = "creator_add" class= "btn btn-sm btn-primary btn-margins" onclick = "saveCreator()"> <span class="glyphicon glyphicon-send"> </span> Add </button>');
	$('.add_creator')
			.append(
					'<button id="cancel_creator_add" class="btn btn-sm edit-tab btn-default btn-margins" onclick="cancelAddCreator()"> <span class="glyphicon glyphicon-remove"></span> Cancel </button>');
	$('#add-creator').css("display", "none");
	$('#add-creator').addClass("hiddencomplete");
	if (!$('#add-creator').hasClass("simple")) {
		$("#creatorAddSelect")
				.select2(
						{
							theme : "bootstrap",
							tags : true,
							delay : 500,
							templateResult : function(item) {
								// No need to template the searching text
								if (item.loading) {
									return item.text;
								}

								var term = query.term || '';
								var $result = markMatch(item.text, term);

								return $result;
							},
							language : {
								searching : function(params) {
									// Intercept the query as it is happening
									query = params;

									// Change this to be appropriate for your
									// application
									return 'Searching…';
								}
							},
							placeholder : "Add a Creator",
							allowClear : true,
							ajax : {
								url : function(params) {
									var term = params.term;
									if (!term) {
										term = "";
									}
									return jsRoutes.api.Metadata.listPeople(
											term, 25).url;
								},
								processResults : function(data, page) {
									return {
										results : data
												.filter(
														function(x) {
															var names = $(
																	'.creator')
																	.map(
																			function() {
																				return $(
																						this)
																						.children(
																								'.authname')
																						.text();
																			});
															return $.inArray(
																	x.name,
																	names) == -1;
														})
												.map(
														function(x) {
															return {
																text : x.name
																		+ ", "
																		+ x['@id']
																		+ ", "
																		+ x.email,
																id : x['@id']
															}
														})
									};
								}
							}
						});

		$("#creatorAddSelect").select2('open');
	} else {
		$("#creatorAddSelect").focus();
	}

	function markMatch(text, term) {
		// Find where the match is
		var match = text.toUpperCase().indexOf(term.toUpperCase());

		var $result = $('<span></span>');

		// If there is no match, move on
		if (match < 0) {
			return $result.text(text);
		}

		// Put in whatever text is before the match
		$result.text(text.substring(0, match));

		// Mark the match
		var $match = $('<span class="select2-rendered__match"></span>');
		$match.text(text.substring(match, match + term.length));

		// Append the matching text
		$result.append($match);

		// Put in whatever is after the match
		$result.append(text.substring(match + term.length));

		return $result;
	}

}

function cancelAddCreator() {
	$('.add_creator').remove();
	$('#add-creator').removeClass("inline");
	$('#add-creator').css("display", "");
	$('#aboutcreators').mouseleave();

}

function saveCreator() {

	var newAuthor = $('#creatorAddSelect').val().trim();
	if ($('.authname').filter(function() {
		return $(this).attr('data-creator') === newAuthor;
	}).length == 0) {

		jsonData = JSON.stringify({
			"creator" : htmlEncode(newAuthor)
		});
		if ($('#aboutcreators').attr('data-dataset')) {
			var request = jsRoutes.api.Datasets.addCreator(
					$('#aboutcreators').attr('data-dataset')).ajax({
				data : jsonData,
				type : 'PUT',
				contentType : "application/json"
			});

			request.done(function(response, textStatus, jqXHR) {
				addCreatorToList(newAuthor);
				$("#creatorlabel").text("Creator(s): ");

				notify("Creator added.", "success", false, 2000);
			});

			request
					.fail(function(jqXHR, textStatus, errorThrown) {
						console.error("The following error occurred: "
								+ textStatus, errorThrown);
						var errMsg = " You must be logged in to update the information about a dataset.";
						if (!checkErrorAndRedirect(jqXHR, errMsg)) {
							notify(
									"The dataset information was not updated due to: "
											+ errorThrown, "error");
						}
					});
		} else {
			addCreatorToList(newAuthor);
		}
	} else {
		notify(newAuthor + " is already in the list of Creators", "error");
	}
}

function addCreatorToList(newCreator) {

	// Add new creator to the end of the list
	if ($('.authname').length != 0) {
		$('.creator-delete').last().parent().append('<span>,</span>');
	}
	$('#ds_creators')
			.append(
					'<span class="creator" ><span class="authname person" data-creator="'
							+ newCreator
							+ '">'
							+ newCreator
							+ '</span><span class="glyphicon glyphicon-remove creator-delete"></span></span>');
	expandPeople();
	// Remove the edit box
	$('.add_creator').remove();
	$('#add-creator').removeClass("inline");
	$('#add-creator').css("display", "");
	$('#aboutcreators').mouseleave();
	if (changeCallback != null) {
		changeCallback();
	}

}

$(document).on(
		"click",
		".creator-delete",
		function() {
			var target = $(this).parent().children('.authname').attr(
					'data-creator').trim();
			if ($('#aboutcreators').attr('data-dataset')) {
				var request = jsRoutes.api.Datasets.removeCreator(
						$('#aboutcreators').attr('data-dataset'), target).ajax(
						{
							type : 'DELETE'
						});

				request.done(function(response, textStatus, jqXHR) {
					console.log("Successful remove user " + target);
					deleteCreatorFromList(target);
					if ($("#ds_creators").children().length == 0) {
						$("#creatorlabel").text("Add creator(s)");
					}
					notify("Creator: " + target + " removed.", "success",
							false, 2000);
				});

				request.fail(function(jqXHR) {
					console.error("ERROR occurs when deleting user " + target);
				});

			} else {
				deleteCreatorFromList(target);
			}
		});

function deleteCreatorFromList(creator) {
	$('.authname').filter(function() {
		return $(this).attr('data-creator') === creator;
	}).parent().remove();
	var lastAuth = $('.authname').last().parent();
	if (lastAuth.children().length == 3) {
		lastAuth.children().last().remove();
	}
	if (changeCallback != null) {
		changeCallback();
	}
}

var changeCallback = null;
function setCreatorChangeCallback(callback) {
	changeCallback = callback;
}

$(function() {

	$(document).on('mouseenter', '#aboutcreators', function() {
		$('#add-creator').removeClass("hiddencomplete");
		$('#add-creator').addClass("inline");
		$('.creator-delete').removeClass("hiddencomplete");
		$('.creator-delete').addClass("glyphicon");

	}).on('mouseleave', '#aboutcreators', function() {
		if (($('.add_creator').length == 0)) {
			$('#add-creator').removeClass("inline");
			$('#add-creator').addClass("hiddencomplete");
			$('.creator-delete').removeClass("glyphicon");
			$('.creator-delete').addClass("hiddencomplete");
		}

	});

	$("#ds_creators").sortable(
			{
				revert : true,
				// containment:"parent",
				placeholder : "sortable-placeholder-highlight",
				forcePlaceholderSize : true,
				forceHelperSize : true,
				beforeStop : function(event, ui) {
					// placeholder is a child of the element under the cursor
					var newIndex = $(ui.placeholder).index() - 1;

					var creatorname = $(ui.helper).children('.authname').attr(
							'data-creator');
					if ($('#aboutcreators').attr('data-dataset')) {
						var request = jsRoutes.api.Datasets.moveCreator(
								$('#aboutcreators').attr('data-dataset'),
								creatorname, newIndex).ajax({
							type : 'PUT',
						});

						request.done(function(response, textStatus, jqXHR) {
							adjustCommasAfterMove(newIndex);

							// Send event
							notify("Creator moved.", "success", false, 2000);
						});

						request.fail(function(jqXHR, textStatus, errorThrown) {
							console.error("The following error occurred: "
									+ textStatus, errorThrown);
							$('#ds_creators').sortable("cancel");
						});
					} else {
						adjustCommasAfterMove(newIndex);
					}
				}
			});
	$("#ds_creators").disableSelection();
});

function adjustCommasAfterMove(newIndex) {
	// Arrange commas
	if (newIndex != ($('.authname').length - 1)) {
		// Add a comma if needed
		var movedAuth = $('.authname').eq(newIndex).parent();
		if (movedAuth.children().length != 3) {
			movedAuth.append("<span>,</span>");
		}
	} else {
		// Add comma after previous last entry
		if (newIndex != 0) {
			var previousLastAuth = $('.authname').eq(newIndex - 1).parent();
			if (previousLastAuth.children().length != 3) {
				previousLastAuth.append("<span>,</span>");
			}
		}
	}

	// Remove comma on last entry if it exists
	var lastAuth = $('.authname').last().parent();
	if (lastAuth.children().length == 3) {
		lastAuth.children().last().remove();
	}
}
