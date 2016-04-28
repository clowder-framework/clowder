	//Counters for DOM node uniqueness.
	var elementCounterDatasets = 1;
	var elementCounterAdd = 1;
	
	var currentFirstDatasets = 1;
	var currentFirstAdd = 1;
	var searchResultsCount = 0;
	
	var datasetsInCollection = $("#collectionDatasetsTable tbody tr");
	var datasetsInCollectionCount = datasetsInCollection.length;
	
	var areRestDatasetsVisible = false;

	function addDataset(datasetId, event){
		
		var request = jsRoutes.api.Collections.attachDataset(collectionId, datasetId).ajax({
			type: 'POST'
		});

		//Note - need to make the "replace" calls below more generic.
		request.done(function (response, textStatus, jqXHR){	        
	        //Remove selected dataset from datasets not in collection.
	        var resultId = event.target.parentNode.parentNode.getAttribute('data-datasetid');
	        var inputDate = $("tr[data-datasetid='" + resultId + "'] td:nth-child(2)").text();
	        var inputDescr = $("tr[data-datasetid='" + resultId + "'] td:nth-child(3)").html();
	        var inputThumbnail = $("tr[data-datasetid='" + resultId + "'] td:nth-child(4)").html(); 	        
	        $("#addDatasetsTable tbody tr[data-datasetid='" + resultId + "']").remove();
	        
	        //Add the node to the contained datasets table, with associated data
	        $('#collectionDatasetsTable tbody').append("<tr data-datasetid='" + datasetId + "'><td><a href='" + jsRoutes.controllers.Datasets.dataset(datasetId).url + "'>"+ event.target.innerHTML.replace(/\n/g, "<br>") + "</a></td>"
					+ "<td>" + inputDate + "</td>"
					+ "<td style='white-space:pre-line;'>" + inputDescr.replace(/\n/g, "<br>") + "</td>"
					+ "<td>" + inputThumbnail + "</td>"
					+ "<td><a href='#!' onclick='removeDataset(\"" + datasetId + "\",event)'>Remove</a>"
					+ "<button class='btn btn-link' title='Detach the Dataset' style='text-align:right' onclick='removeDataset(\"" + datasetId + "\",event)'>"
					+ "<span class='glyphicon glyphicon-trash'></span></button></td></tr>");
		});	
		
		request.fail(function (jqXHR, textStatus, errorThrown){
			console.error("The following error occured: "+textStatus, errorThrown);
	        var errMsg = "You must be logged in to add a dataset to a collection.";
	        if (!checkErrorAndRedirect(jqXHR, errMsg)) {
	            notify("The dataset was not added to the collection due to : " + errorThrown, "error");
	        }    		
 		});
		
	}
	
	function removeDatasetFromCollection(datasetId, event){
		
		var request = jsRoutes.api.Collections.removeDataset(collectionId, datasetId).ajax({
			type: 'POST'
		});
		
		request.done(function (response, textStatus, jqXHR){	        	       
	      //Remove selected dataset from datasets in collection.
			$('#'+datasetId).remove();
		});  	
		
		request.fail(function (jqXHR, textStatus, errorThrown){
			console.error("The following error occured: "+textStatus, errorThrown);
	        var errMsg = "You must be logged in to remove a dataset from a collection.";
	        if (!checkErrorAndRedirect(jqXHR, errMsg)) {
	            notify("The dataset was not removed from the collection due to : " + errorThrown, "error");
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
			var currRowDate = $("#addDatasetsTable tbody tr[id='resultRow" + pos + "'] td:nth-child(2)").text().split(" ");
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
	
	
	datasetsInCollection.slice(0,10).each(function() {
			$(this).css('display','table-row');	
	});
	if(datasetsInCollection.length > 10)
		$('#datasetsPagerNext').css('visibility','visible');	
	datasetsInCollection.each(function() {
		$(this).attr("id","datasetRow" + elementCounterDatasets);
		elementCounterDatasets++;
	});
	
	 $('body').on('click','#datasetsPagerNext',function(e){
		 currentFirstDatasets = currentFirstDatasets + 10;
		 $("#collectionDatasetsTable tbody tr").each(function() {
        	    $(this).css('display','none');
         });
		 for(var i = currentFirstDatasets; i < currentFirstDatasets + 10; i++){
			 $("#collectionDatasetsTable tbody tr[id='datasetRow" + i + "']").each(function() {
				 $(this).css('display','table-row');
			 });
		 }
		 $('#datasetsPagerPrev').css('visibility','visible');
		 if(currentFirstDatasets + 10 > datasetsInCollectionCount)
			 $('#datasetsPagerNext').css('visibility','hidden');
		 
		 return false;
	 });
	 $('body').on('click','#datasetsPagerPrev',function(e){
		 currentFirstDatasets = currentFirstDatasets - 10;
		 $("#collectionDatasetsTable tbody tr").each(function() {
        	    $(this).css('display','none');
         });
		 for(var i = currentFirstDatasets; i < currentFirstDatasets + 10; i++){
			 $("#collectionDatasetsTable tbody tr[id='datasetRow" + i + "']").each(function() {
				 $(this).css('display','table-row');
			 });
		 }
		 if(currentFirstDatasets + 10 <= datasetsInCollectionCount)
			 $('#datasetsPagerNext').css('visibility','visible');
		 if(currentFirstDatasets == 1)
			 $('#datasetsPagerPrev').css('visibility','hidden');
		 
		 return false;
	 });

		
