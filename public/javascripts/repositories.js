function expandRepositories() {
	var repoMap = new Object();
	var repoSet = new Set();
	var requestList = [];
	var i = 0;
	// Collect the list of all unique repo orgidentifiers being used
	$('.repository').each(function() {
		repoSet.add(this.textContent);

	})

	// ajax get each one
	repoSet.forEach(function(nextRepo) {
		var request = jsRoutes.api.Metadata.getRepository(nextRepo).ajax({
			type : 'GET',
			dataType : "json"
		});
		requestList.push(request);
		request.done(function(repo) {

			repoMap[repo.orgidentifier] = repo;

		});

		request.fail(function(jqXHR, textStatus, errorThrown) {
			if (jqXHR.status != 404) {
				console.error("The following error occured: " + textStatus,
						errorThrown);
			}
		});

	});

	// When all ajax requests have completed, expand each repo element
	$.when.apply(this, requestList).done(function() {
		$('.repository').each(function() {

			var repoElement = this;
			if (!$(repoElement).hasClass('expanded')) {
				if (repoMap.hasOwnProperty(this.textContent)) {

					$(repoElement).addClass('expanded');
					expandRepo(repoElement, repoMap[this.textContent]);
				}
			}
		});
	})
}

var i = 0;
function expandRepo(repoElement, repo) {
	var name = repo.repositoryName;

	var html = "<a href='" + repo.repositoryURL + "' target=_blank>" + name
			+ "</a>";
	repoElement.innerHTML = html;

	if (repo.hasOwnProperty('description')) {
		var tip = repo.description;
		if (repo.description.hasOwnProperty('content')) {

			tip = repo.description.content;
		}
		$(repoElement)
				.popover(
						{
							content : tip,
							placement : 'top',
							template : '<div class="popover" role="tooltip" style="max-width:600px;word-break:break-all"><div class="arrow"></div><h3 class="popover-title"></h3><div class="popover-content"></div></div>'
						});
		repoElement.onmouseenter = function() {
			$(this).popover('show');
		};
		repoElement.onmouseleave = function() {
			$(this).popover('hide');
		};
	}

}

$(function() {
	expandRepositories();
})