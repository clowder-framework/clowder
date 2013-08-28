	//Counters for DOM node uniqueness.
	var elementCounterDatasets = 1;
	var elementCounterAdd = 1;
	
	var currentFirstDatasets = 1;
	var currentFirstAdd = 1;
	var searchResultsCount = 0;
	
	var datasetsInCollection = $("#collectionDatasetsTable tbody tr");
	var datasetsInCollectionCount = datasetsInCollection.length;

	function addDataset(datasetId, resultId, event){
		var request = $.ajax({
		       type: 'POST',
		       url: "http://"+hostIp+":"+window.location.port+"/api/collections/"+collectionId+"/datasets/"+datasetId
		     });
		request.done(function (response, textStatus, jqXHR){
	        console.log("Response " + response);	        
	        //Remove selected dataset from datasets not in collection.
	        $("#addDatasetsTable tbody tr[id='" + resultId + "']").remove();
	        $("#addDatasetsTable tbody tr[id='resultRow" + (currentFirstAdd+10) + "']").css('display','table-row');
	        searchResultsCount--;
	        if(currentFirstAdd + 10 > searchResultsCount)
				 $('#addPagerNext').css('visibility','hidden');
	        for(var i = parseInt(resultId.replace("resultRow",""))+1; i <= searchResultsCount + 1; i++){
				 $("#addDatasetsTable tbody tr[id='resultRow" + i + "']").each(function() {
					 $(this)[0].innerHTML = $(this)[0].innerHTML.replace("resultRow" + i, "resultRow" + (i-1));
					 $(this).attr("id", "resultRow" + (i-1));				 
				 });
			 }
	        if(currentFirstAdd > searchResultsCount)
	        	if($('#addPagerPrev').is(':visible'))
	        		$('#addPagerPrev').click();
	        
	        //Add selected dataset to collection
	        for(var i = datasetsInCollectionCount; i >= 1; i--){
				 $("#collectionDatasetsTable tbody tr[id='datasetRow" + i + "']").each(function() {
					 $(this).attr("id", "datasetRow" + (i+1));
				 });
			 }
	        datasetsInCollectionCount++;
	        var addedDatasetRow = event.target.parentNode.parentNode;
	        $('#collectionDatasetsTable tbody').prepend("<tr id='datasetRow1' style='display:none;'><td><a href='" + "http://" + hostIp + ":" + window.location.port
					+ "/datasets/" + datasetId + "'>"+ event.target.innerHTML + "</a></td>"
					+ "<td>" + $("#" + addedDatasetRow.getAttribute('id') + " td:nth-child(2)").text() + "</td>"
					+ "<td>" + $("#" + addedDatasetRow.getAttribute('id') + " td:nth-child(3)").text() + "</td></tr>");
	        $('#collectionDatasetsTable tbody tr').css('display','none');
	        for(var i = 0; i < 10; i++){
	        	$("#collectionDatasetsTable tbody tr[id='datasetRow" + (i+1) + "']").each(function() {
	        	    $(this).css('display','table-row');
	        	});
	        }
	        $('#datasetsPagerPrev').css('visibility','hidden');
	        if(datasetsInCollectionCount > 10)
	        	$('#datasetsPagerNext').css('visibility','visible');
	        else
	        	$('#datasetsPagerNext').css('visibility','hidden');
	        
	        currentFirstDatasets = 1;
		});	
		request.fail(function (jqXHR, textStatus, errorThrown){
    		console.error(
        		"The following error occured: "+
        		textStatus, errorThrown		            
    			);
    		alert("ERROR: " + errorThrown +". Dataset not added to collection." );
 			});
		
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
		 $('#datasetsPagerNext').css('visibility','visible');
		 if(currentFirstDatasets == 1)
			 $('#datasetsPagerPrev').css('visibility','hidden');			 
	 });
	 
	 $('body').on('click','#addDatasetBtn',function(e){
			var request = $.ajax({
		       type: 'GET',
		       url: queryIp,
		       dataType: "json",
		     });
			request.done(function (respJSON){
		        console.log("Response " + respJSON);
		        $('#addPagerPrev').css('visibility','hidden');
		        $('#addPagerNext').css('visibility','hidden');
		        searchResultsCount = respJSON.length;
		        $('#addDatasetsTable tbody tr').remove();
		        for(var i = 0; i < respJSON.length; i++){
		        	var createdDateArray = respJSON[i].created.split(" ");
		        	var createdDate = createdDateArray.slice(1,3).join(" ") + ", " + createdDateArray[5];
		        	$('#addDatasetsTable tbody').append("<tr id='resultRow" + (i+1) + "' style='display:none;'><td><a href='#' "
		        								+ "onclick='addDataset(\"" + respJSON[i].id + "\",\"resultRow" + (i+1) + "\",event)' "
		        								+ ">"+ respJSON[i].datasetname + "</a></td>"
		        								+ "<td>" + createdDate + "</td>"
		        								+ "<td>" + respJSON[i].description + "</td>"
		        								+ "<td><a target='_blank' href='" +  "http://" + hostIp + ":" + window.location.port			
		        								+ "/datasets/" + respJSON[i].id + "'>View</a></td></tr>");
		        }
		        $('#addDatasetsTable').show();
		        
		        for(var i = 0; i < 10; i++){
		        	$("#addDatasetsTable tbody tr[id='resultRow" + (i+1) + "']").each(function() {
		        	    $(this).css('display','table-row');
		        	});
		        }
		        
		        if(respJSON.length > 10){
		        	currentFirstAdd = 1;
		        	$('#addPagerNext').css('visibility','visible');
		        }
		        
		        $("#hideAddDatasetBtn").show();
 			});
			request.fail(function (jqXHR, textStatus, errorThrown){
        		console.error(
            		"The following error occured: "+
            		textStatus, errorThrown		            
        			);
        		alert("ERROR: " + errorThrown +"." );
     			});		 
	 });
	 $('body').on('click','#hideAddDatasetBtn',function(e){
		 $('#addPagerPrev').css('visibility','hidden');
	     $('#addPagerNext').css('visibility','hidden');
	     $('#addDatasetsTable tbody tr').remove();
	     $('#addDatasetsTable').css('display','none');
	     $('#hideAddDatasetBtn').css('display','none');
	 });
	
	 $('body').on('click','#addPagerNext',function(e){
		 currentFirstAdd = currentFirstAdd + 10;
		 $("#addDatasetsTable tbody tr").each(function() {
        	    $(this).css('display','none');
         });
		 for(var i = currentFirstAdd; i < currentFirstAdd + 10; i++){
			 $("#addDatasetsTable tbody tr[id='resultRow" + i + "']").each(function() {
				 $(this).css('display','table-row');
			 });
		 }
		 $('#addPagerPrev').css('visibility','visible');
		 if(currentFirstAdd + 10 > searchResultsCount)
			 $('#addPagerNext').css('visibility','hidden');
	 });
	 $('body').on('click','#addPagerPrev',function(e){
		 currentFirstAdd = currentFirstAdd - 10;
		 $("#addDatasetsTable tbody tr").each(function() {
        	    $(this).css('display','none');
         });
		 for(var i = currentFirstAdd; i < currentFirstAdd + 10; i++){
			 $("#addDatasetsTable tbody tr[id='resultRow" + i + "']").each(function() {
				 $(this).css('display','table-row');
			 });
		 }
		 $('#addPagerNext').css('visibility','visible');
		 if(currentFirstAdd == 1)
			 $('#addPagerPrev').css('visibility','hidden');			 
	 });	
		
		
