	//Counters for DOM node uniqueness.
	var elementCounterChildCollections = 1;
	var elementCounterAdd = 1;

	var currentFirstChildCollections = 1;
	var currentFirstAdd = 1;
	var searchResultsCount = 0;

	var childCollectionsInCollection = $("#collectionChildCollectionsTable tbody tr");
	var childCollectionsInCollectionCount = childCollectionsInCollection.length;

	var areRestChildCollectionsVisible = false;

	function addChildCollection(childCollectionId, event){
		console.log("add child collection in collection__childcollectionslist called");
		var request = jsRoutes.api.Collections.attachSubCollection(collectionId,childCollectionId).ajax({
			type: 'POST'
		});

		//Note - need to make the "replace" calls below more generic.
		request.done(function (response, textStatus, jqXHR){
	        //Remove selected dataset from datasets not in collection.
			var target = event.target;
			var parentNode = event.parentNode;
	        var resultId = event.target.parentNode.parentNode.getAttribute('data-childCollectionId');
	        var inputDate = $("tr[data-childCollectionId='" + resultId + "'] td:nth-child(2)").text();
	        var inputDescr = $("tr[data-childCollectionId='" + resultId + "'] td:nth-child(3)").html();
	        var inputThumbnail = $("tr[data-childCollectionId='" + resultId + "'] td:nth-child(4)").html();
	        $("#addChildCollectionsTable tbody tr[data-childCollectionId='" + resultId + "']").remove();

	        //Add the node to the contained datasets table, with associated data
	        $('#collectionChildCollectionsTable tbody').append("<tr data-childCollectionId='" + childCollectionId + "'><td><a href='" + jsRoutes.controllers.Collections.collection(childCollectionId).url + "'>"+ event.target.innerHTML.replace(/\n/g, "<br>") + "</a></td>"
					+ "<td>" + inputDate + "</td>"
					+ "<td style='white-space:pre-line;'>" + inputDescr.replace(/\n/g, "<br>") + "</td>"
					+ "<td>" + inputThumbnail + "</td>"
					+ "<td><a href='#!' onclick='removeChildCollection(\"" + childCollectionId + "\",event)'>Remove</a>"
					+ "<button class='btn btn-link' title='Remove Child Collection' style='text-align:right' onclick='removeChildCollection(\"" + childCollectionId + "\",event)'>"
					+ "<span class='glyphicon glyphicon-trash'></span></button></td></tr>");
		});

		request.fail(function (jqXHR, textStatus, errorThrown){
			console.error("The following error occured: "+textStatus, errorThrown);
	        var errMsg = "You must be logged in to add a child collection to a collection.";
	        if (!checkErrorAndRedirect(jqXHR, errMsg)) {
	            notify("The dataset was not added to the collection due to : " + errorThrown, "error");
	        }
 		});

	}

	function removeChildCollection(childCollectionId, event){

		var request = jsRoutes.api.Collections.removeSubCollection(collectionId, childCollectionId).ajax({
			type: 'POST'
		});

		request.done(function (response, textStatus, jqXHR){
	      //Remove selected dataset from datasets in collection.
			$('#'+childCollectionId).remove();
		});

		request.fail(function (jqXHR, textStatus, errorThrown){
			console.error("The following error occured: "+textStatus, errorThrown);
	        var errMsg = "You must be logged in to remove a child collection from a collection.";
	        if (!checkErrorAndRedirect(jqXHR, errMsg)) {
	            notify("The child collection was not removed from the collection due to : " + errorThrown, "error");
	        }
 		});
	}

	function findPos(reqNode){

		var dateString = reqNode.children[1].innerHTML.split(" ");
		dateString[1] = dateString[1].replace(",","");
		dateString[0] = dateString[0].replace("Jan","01");
		dateString[0] = dateString[0].replace("Feb","02");
		dateString[0] = dateString[0].replace("Mar","03");
		dateString[0] = dateString[0].replace("Apr","04");
		dateString[0] = dateString[0].replace("May","05");
		dateString[0] = dateString[0].replace("Jun","06");
		dateString[0] = dateString[0].replace("Jul","07");
		dateString[0] = dateString[0].replace("Aug","08");
		dateString[0] = dateString[0].replace("Sep","09");
		dateString[0] = dateString[0].replace("Oct","10");
		dateString[0] = dateString[0].replace("Nov","11");
		dateString[0] = dateString[0].replace("Dec","12");
		for(var pos = 1;pos <= searchResultsCount; pos++){
			var currRowDate = $("#addChildCollectionsTable tbody tr[id='resultRow" + pos + "'] td:nth-child(2)").text().split(" ");
			currRowDate[1] = currRowDate[1].replace(",","");
			if(dateString[2] > currRowDate[2])
				return pos;
			else if(dateString[2] < currRowDate[2])
				continue;
			else{
				currRowDate[0] = currRowDate[0].replace("Jan","01");
				currRowDate[0] = currRowDate[0].replace("Feb","02");
				currRowDate[0] = currRowDate[0].replace("Mar","03");
				currRowDate[0] = currRowDate[0].replace("Apr","04");
				currRowDate[0] = currRowDate[0].replace("May","05");
				currRowDate[0] = currRowDate[0].replace("Jun","06");
				currRowDate[0] = currRowDate[0].replace("Jul","07");
				currRowDate[0] = currRowDate[0].replace("Aug","08");
				currRowDate[0] = currRowDate[0].replace("Sep","09");
				currRowDate[0] = currRowDate[0].replace("Oct","10");
				currRowDate[0] = currRowDate[0].replace("Nov","11");
				currRowDate[0] = currRowDate[0].replace("Dec","12");
				if(dateString[0] > currRowDate[0])
					return pos;
				else if(dateString[0] < currRowDate[0])
					continue;
				else
					if(dateString[1] > currRowDate[1])
						return pos;
					else if(dateString[1] < currRowDate[1])
						continue;
					else
						return pos;
			}
		}
		return searchResultsCount+1;
	}


	childCollectionsInCollection.slice(0,10).each(function() {
			$(this).css('display','table-row');
	});
	if(childCollectionsInCollection.length > 10)
		$('#childCollectionsPagerNext').css('visibility','visible');
	childCollectionsInCollection.each(function() {
		$(this).attr("id","datasetRow" + elementCounterChildCollections);
		elementCounterChildCollections++;
	});

	 $('body').on('click','#childCollectionsPagerNext',function(e){
		 currentFirstChildCollections = currentFirstChildCollections + 10;
		 $("#collectionChildCollectionsTable tbody tr").each(function() {
        	    $(this).css('display','none');
         });
		 for(var i = currentFirstChildCollections; i < currentFirstChildCollections + 10; i++){
			 $("#collectionChildCollectionsTable tbody tr[id='datasetRow" + i + "']").each(function() {
				 $(this).css('display','table-row');
			 });
		 }
		 $('#childCollectionsPagerPrev').css('visibility','visible');
		 if(currentFirstChildCollections + 10 > childCollectionsInCollectionCount)
			 $('#childCollectionsPagerNext').css('visibility','hidden');

		 return false;
	 });
	 $('body').on('click','#childCollectionsPagerPrev',function(e){
		 currentFirstChildCollections = currentFirstChildCollections - 10;
		 $("#collectionChildCollectionsTable tbody tr").each(function() {
        	    $(this).css('display','none');
         });
		 for(var i = currentFirstChildCollections; i < currentFirstChildCollections + 10; i++){
			 $("#collectionChildCollectionsTable tbody tr[id='datasetRow" + i + "']").each(function() {
				 $(this).css('display','table-row');
			 });
		 }
		 if(currentFirstChildCollections + 10 <= childCollectionsInCollectionCount)
			 $('#childCollectionsPagerNext').css('visibility','visible');
		 if(currentFirstChildCollections == 1)
			 $('#childCollectionsPagerPrev').css('visibility','hidden');

		 return false;
	 });

