console.log("top of java script");
var fileTypeList = [];
var fileTypeState = false;
var fileTypeSelected;

var indexName="";

var allAdapterList=[];
var shortAdapterList=[];

var allExtractorList=[];
var shortExtractorList=[];

var allMeasureList=[];
var shortMeasureList=[];

var adapterState= false;
var extractorState=false;
var measureState= false;

var adapterSelected;
var extractorSelected;
var measureSelected;

var indexerState=false;
var indexState=false;
var indexerSelected;
var indexSelected="";

var indexerList=[];
var indexList=[];

$(document).ready(function (){
	getAllAdapters();//global car adapterList is now set
	getAllExtractors();//global var allExtractorList is now set
	getAllMeasures();//global  allMeasureList is now set
	getIndexers();//global  indexerList is now set
	getIndexes();//global  indexerList is now set	
});

function setNameForIndex(){
	indexName = nameTextArea.value
	console.log("inside setNameForIndex = " + indexName)	
}

//Called when page is loaded
function getAllAdapters() {
	var request = jsRoutes.controllers.Admin.getAdapters().ajax({
		type: 'GET'
	});
	
	request.done(function (json_response, textStatus, jqXHR){
	    var mimeTypes = "";	
    	for(var i=0; i<json_response.length; i++){
    		mimeTypes = json_response[i].supportedMimeTypes;		
    		allAdapterList.push({name:json_response[i].name,
    			id:json_response[i].id,
    			mimeTypes:json_response[i].supportedMimeTypes, 
    			interfaces:json_response[i].interfaces});			
    	}
	});	
	
	request.fail(function (jqXHR, textStatus, errorThrown){
		console.error("The following error occured: "+textStatus, errorThrown);
	});  	
}

//Called when page is loaded
function getAllExtractors() {
	var request = jsRoutes.controllers.Admin.getExtractors().ajax({
		type: 'GET'
	});	
	request.done(function (data, textStatus, jqXHR){
    	for(var i=0; i<data.length; i++){
    		allExtractorList.push({name:data[i].name, 
    									id:data[i].id, 
    									supportedFeature:data[i].supportedFeature, 
    									supportedAdapters:data[i].supportedAdapters});					
    	}
	});	
	request.fail(function (jqXHR, textStatus, errorThrown){
		console.error("The following error occured: "+textStatus, errorThrown);
	});	
}	

//Called when page is loaded
function getAllMeasures() {
	var request = jsRoutes.controllers.Admin.getMeasures().ajax({
		type: 'GET'
	});	
	request.done(function (json, textStatus, jqXHR){
		for(var i=0; i<json.length; i++){
				console.log(". ");
				allMeasureList.push({name:json[i].name,id:json[i].id,supportedFeatureType:json[i].supportedFeatureType});
		}
	});
	request.fail(function (jqXHR, textStatus, errorThrown){
		console.error("The following error occured: "+textStatus, errorThrown);
	});  	
}


//Called when page is loaded
function getIndexers() {
	var request = jsRoutes.controllers.Admin.getIndexers().ajax({
		type: 'GET'
	});	
	request.done(function (json, textStatus, jqXHR){
		var availableIndexers = "";
		for(var i=0; i<json.length; i++){
			availableIndexers = availableIndexers+'<option value="'+json[i].id+'" >'+json[i].name+'</option>';
		    indexerList[i] = {name:json[i].name,id:json[i].id};
		}
		$("#indexerDropDown").html('<option value="invalid">--Indexer--</option>'+availableIndexers);
	});		
	request.fail(function (jqXHR, textStatus, errorThrown){
		console.error("The following error occured: "+textStatus, errorThrown);
	}); 
}


//Called when page is loaded
function getIndexes() {
	var request = jsRoutes.controllers.Admin.getIndexes().ajax({
		type: 'GET'
	});	
	request.done(function (json, textStatus, jqXHR){
		var availableIndexes = "";
    	for(var i=0; i<json.length; i++){	
    		console.log("available index id = " +json[i].indexID );
    		availableIndexes = availableIndexes+'<option value="'+json[i].indexID+'" >'+json[i].indexID+'</option>';
    		indexList[i] = {indexID:json[i].indexID,MIMEtype:json[i].MIMEtype,Extractor:json[i].Extractor, Measure:json[i].Measure,Indexer:json[i].Indexer};
    	}
    	$("#indexDropDownList").html('<option value="invalid">--Index--</option>'+availableIndexes);
    	$("#indexDel").html('<option value="invalid">--Index--</option>'+availableIndexes);
	});		
	request.fail(function (jqXHR, textStatus, errorThrown){
		console.error("The following error occured: "+textStatus, errorThrown);
	});
}

//Called when a file type is selected from drop down list
function setFileTypeAndAvailableAdapters(){
	//
	//started creating a new index - first reset everything
	//
	document.getElementById("createIndexFeedback").innerHTML = "";
	adapterSelected=null;
	extractorSelected=null;
	measureSelected=null;
	indexerSelected=null;
	
	$("#extractorDropDown").html('<option value="invalid">--Extractor--</option>');	
	$("#measureDropDown").html('<option value="invalid">--Measure--</option>');
	document.getElementById("indexerDropDown").selectedIndex = 0;
	
	//
	//process new file type
	//
	if($("#fileTypeDropDown").val() == "invalid"){
		fileTypeState = false;
	}
	else{
		fileTypeState    = true;
		fileTypeSelected =  $("#fileTypeDropDown").val()
	
		//indexType should be a part of the extractor ID 
  		if (fileTypeSelected == "sectionCensus"){
       		 indexType = "census"
       		 fileTypeSelected = "image"
       	} else if (fileTypeSelected == "sectionFace"){
       		 indexType = "face"
       		 fileTypeSelected = "image"
       	}  else if (fileTypeSelected == "sectionCinemetrics"){
       		 indexType = "cinemetrics"
       		 fileTypeSelected = "image"
       	}   else {
      		indexType=""
       	}   		
		setShortAdapterList(fileTypeSelected);
	}	
}

//Create and set list of adapters that can process the given type of file.
function setShortAdapterList(type)
{
	var availableAdapters="";	
	for(var i=0; i<allAdapterList.length; i++){
		var mimeTypes = allAdapterList[i].mimeTypes;					
		var bool = false;
		for (var j=0; j< mimeTypes.length; j++){
			if (mimeTypes[j].indexOf(type) != -1){
				bool = true;
			}
		}		
		if (bool) {			
			availableAdapters  = availableAdapters+'<option value="'+allAdapterList[i].id+'" >'+allAdapterList[i].name+'</option>';			
			shortAdapterList.push(allAdapterList[i]);	
		}			
	}	
	$("#adapterDropDown").html('<option value="invalid">--Adapter--</option>'+availableAdapters);
}

//Called once selected an adapter from drop down list.
//For selected adapter, will set adapterSelected variable and list of adapter interfaces
function setAdapterTypeAndAvailableExtractors(){
	var adapterInterfaces =[];	
	if($("#adapterDropDown").val() == "invalid"){
		adapterState = false;$("#extractorDropDown").html('<option value="invalid">--Extractor--</option>'+availableExtractors);
	} else{
		adapterState    = true;
		for( var i=0; i<shortAdapterList.length; i++){
			if($("#adapterDropDown").val() == shortAdapterList[i].id ){				
				adapterInterfaces = shortAdapterList[i].interfaces;
				adapterSelected=shortAdapterList[i].id.split(" ")[1];
			}
		}		
		setShortExtractorList(adapterInterfaces);
	}
}

/*
 * Finds extractors that match given array of interfaces. 
 * For selected adapter, will display all matching extractors.
 */
function setShortExtractorList(interfacesArray){
	//interfaces that this adapter is extending
	var availableExtractors ="";
	for(var i=0; i<allExtractorList.length; i++){		
		var supportedAdaptersArray = allExtractorList[i].supportedAdapters;

		//need to find intersection of two arrays - interfaces and supportedAdapters.
		//if intersection is not empty then this extractor supports this adapter.
		
		//for i's extractor, go through all the interfaces that adapter is extending and find 
		//which of these interfaces the extractor supports.
		var results = [];
		for (var k = 0; k < interfacesArray.length; k++) {
    		if (supportedAdaptersArray.indexOf(interfacesArray[k]) !== -1) {
    			//console.log("found intersection " + interfacesArray[k]);
        		results.push(interfacesArray[k]);
    		}
		}		
		if (results.length ==0){
			console.log("no intersection found => no extactors for this adapter found.");
		} else {
			//intersection is non empty => this extractor supports this adapter, so add it to the short list.
			availableExtractors  = availableExtractors+'<option value="'+allExtractorList[i].id+'" >'+allExtractorList[i].name+'</option>';			
			shortExtractorList.push(allExtractorList[i]);
		}		
	}
	$("#extractorDropDown").html('<option value="invalid">--Extractor--</option>'+availableExtractors);
}

/*
 * Called when an extractor is selected from drop down list.
 */
function setExtractorTypeAndAvailableMeasures(){
	var supportedFeature;	
	if($("#extractorDropDown").val() == "invalid"){
		extractorState = false;
	} else {
		extractorState    = true;
		for( var i=0; i<shortExtractorList.length; i++){
			if($("#extractorDropDown").val() == shortExtractorList[i].id ){
				supportedFeature = shortExtractorList[i].supportedFeature;
				extractorSelected=shortExtractorList[i].id.split(" ")[1];
			}
		}
		setShortMeasureList(supportedFeature);
	}
}

/*
 * List of measures that match given feature type
 */
function setShortMeasureList(featureType)
{
	var availableMeasures="";
	for(var i=0; i<allMeasureList.length; i++){
		var featureTypes = allMeasureList[i].supportedFeatureType;
		//go through list of all features this measure supports
		var bool = false; 		
		for (var j=0; j<featureTypes.length; j++){
		if (featureTypes[j].indexOf(featureType) != -1){
				bool = true;
			}		
		}		
		if (bool) {			
			availableMeasures  = availableMeasures+'<option value="'+allMeasureList[i].id+'" >'+allMeasureList[i].name+'</option>';	
			shortMeasureList.push(allMeasureList[i]);	
		}			
	}		
	$("#measureDropDown").html('<option value="invalid">--Measure--</option>'+availableMeasures);
}

/*
 * Called when a measure is selected from drop down list
 */
function setMeasureType(){
	if($("#measureDropDown").val() == "invalid"){
		console.log("measure not selected");
		measureState = false;
	} else{
		measureState    = true;
		for( var i=0; i<shortMeasureList.length; i++){
			if($("#measureDropDown").val() == shortMeasureList[i].id ){
				console.log("Measure name selected: ",shortMeasureList[i].name," shortMeasureList[i].id=",shortMeasureList[i].id);
				measureSelected=shortMeasureList[i].id.split(" ")[1];
			}
		}
	}
}

function setIndexerState(){
	if($("#indexerDropDown").val() == "invalid"){
		indexerState = false;
	}
	else{
		indexerState    = true;
		for( var i=0; i<indexerList.length; i++){
			if($("#indexerDropDown").val() == indexerList[i].id ){
				console.log("indexerList",$("#indexerDropDown").val());
				console.log("Indexer name selected: ",indexerList[i].name," indexerList[i].id=",indexerList[i].id);
				indexerSelected=indexerList[i].id;
			}
		}
	}
}


function setIndexState(){
	if($("#indexDropDownList").val() == "invalid"){
		indexState = false;
	}
	else{
		indexState    = true;
		for( var i=0; i<indexList.length; i++){
			if($("#indexDropDownList").val() == indexList[i].indexID ){
				indexSelected=indexList[i].indexID;
			}
		}		
		console.log("indexSelected=",indexSelected);
	}	
}

function setIndexDelState(){
	if($("#indexDel").val() == "invalid"){
		indexState = false;
	}
	else{
		indexState    = true;
		for( var i=0; i<indexList.length; i++){
			if($("#indexDel").val() == indexList[i].indexID ){
				indexSelected=indexList[i].indexID;
			}
		}		
	}	
}

var createBtn=document.getElementById('create');
    createBtn.onclick=function(evt){
    console.log("========Inside createBtn onclick function");
     
    var request = jsRoutes.controllers.Admin.createIndex().ajax({
		type: 'POST',
		contentType: "application/json",
        data:JSON.stringify({"name":indexName, "adapter":adapterSelected, "extractor":extractorSelected, "measure":measureSelected, "indexer":indexerSelected,"indexType":indexType})
	});	
    request.done(function (data, textStatus, jqXHR){
    	console.log("data:", data);
        console.log("Sucessfully created");
        //update list of indexes everywhere on the page
	    getIndexes();
		//remove old info messages
	    resetDisplay();
        //display info message for create index
        $('#createIndexFeedback').text( data);  
	});	
    request.fail(function (jqXHR, textStatus, errorThrown){
		console.error("The following error occured: "+textStatus, errorThrown);
        $('#createIndexFeedback').text( "Index could not be created");
	});	    	
}	    
    			
    
    
var buildBtn=document.getElementById('build');
    buildBtn.onclick=function(evt){    
    	console.log("========Inside buildBtn onclick function, index = ", indexSelected);    
    	var request=jsRoutes.controllers.Admin.buildIndex(indexSelected).ajax({
   					 type:'POST'
    	});
    	request.done(function (response, textStatus, jqXHR){
					//remove old info messages
	                resetDisplay();
    				$('#buildmsg').text(response);
    	});
    	request.fail(function (jqXHR, textStatus, errorThrown){
    		console.error("The following error occured: "+textStatus, errorThrown);
            $('#buildmsg').text( "Could not build index");
    	});	
}	
        
    	
var listBtn=document.getElementById('list');
    listBtn.onclick=function(evt){    
        console.log("========Inside listBtn onclick function");
    	var request=jsRoutes.controllers.Admin.getIndexes().ajax({
   					 type:'GET',
   					 dataType: "json",
    				});
    				request.done(function (json, textStatus, jqXHR){
						console.log("Response =" + json);
						//remove old info messages
    	                resetDisplay();
						
						var tab=[];
						if (json.length>0){
						tab.push('<table id="inputTable" class="table table-bordered table-striped" style="display:inline;"><th>Name of Index</th><th>Type of Index</th><th>IndexId</th><th>Extractor</th><th>Measure</th><th>Indexer</th>');
                        
						for(var i=0; i<json.length; i++){						
								indexList[i] = {indexID:json[i].indexID,MIMEtype:json[i].MIMEtype,Extractor:json[i].Extractor, Measure:json[i].Measure,Indexer:json[i].Indexer};
									tab.push('<tr id=i >',
							      	'<td>',  json[i].indexName, '</td>',
							      	'<td>',  json[i].indexType, '</td>',
							    	'<td>', json[i].indexID, '</td>',
							      	'<td>',json[i].Extractor,' </td>',
							      	'<td>',json[i].Measure,'</td>',
							      	'<td>',json[i].Indexer,'</td>',
							      	'</tr>');							
							}
						tab.push('</table>');
						document.getElementById('listIndexesTable').innerHTML=tab.join('');		
						}
						if (json.length==0){						
							document.getElementById('listIndexesTable').innerHTML="No indexes found";	
						}						
   					 });
    				request.fail(function (jqXHR, textStatus, errorThrown){
    		    		console.error("The following error occured: "+textStatus, errorThrown);
    		    	});
    	    }		


    var deleteBtn=document.getElementById('delete');
    deleteBtn.onclick=function(evt){ 
        console.log("========Inside deleteBtn onclick function");
    	var request=jsRoutes.controllers.Admin.deleteIndex(indexSelected).ajax({
   					 type:'DELETE'
    				});
    				request.done(function (response, textStatus, jqXHR){
    					//update list of indexes everywhere on the page
    	    	    	getIndexes();
    	    			//remove old info messages
    	    	        resetDisplay();
    					//display info msg for delete index  
    					$('#deletemsg').text(response);    			
   					 });
    				request.fail(function (jqXHR, textStatus, errorThrown){
    		    		console.error("The following error occured: "+textStatus, errorThrown);
    		            $('#deletemsg').text( "Could not delete index");
    		    	});
}		
 
    
	var deleteAllBtn=document.getElementById('deleteAll');
    	deleteAllBtn.onclick=function(evt){    
        	console.log("========Inside deleteAllBtn onclick function");
    		var request=jsRoutes.controllers.Admin.deleteAllIndexes().ajax({
   				type:'DELETE'
    		});
    		
    		request.done(function (response, textStatus, jqXHR){
    			//update list of indexes everywhere on the page
    	    	getIndexes();
    			//remove old info messages
    	        resetDisplay();
    			//display info msg for delete all indexes
    			$('#deleteallmsg').text(response);			
           });
    		request.fail(function (jqXHR, textStatus, errorThrown){
	    		console.error("The following error occured: "+textStatus, errorThrown);
	            $('#deleteallmsg').text( "Could not delete all indexes");
	    	});
	}

var reindex=document.getElementById('reindexElasticsearch');
reindex.onclick=function(evt){
	var request= jsRoutes.api.Admin.reindex().ajax({
		type:'POST',
		beforeSend: function( xhr ) {
			$('#reindexmsg').text( "In Progress");
		}
	});

	request.done(function (response, textStatus, jqXHR){
		//display info msg for reindex
		$('#reindexmsg').text(response.status);
	});
	request.fail(function (jqXHR, textStatus, errorThrown){
		console.error("The following error occured: "+textStatus, errorThrown);
		$('#reindexmsg').text( "Could not reindex");
	});
}
    
   /*
    *Called when list of indexes changed  - created or deleted an index, or deleted all.
    */
    function resetDisplay(){
    	console.log("===top of resetDisplay");
    	//hide the table with all indexes
    	document.getElementById('listIndexesTable').innerHTML="";
    	//hide old info messages (create, build, delete, deleteAll)
    	document.getElementById("createIndexFeedback").innerHTML = "";
    	$('#buildmsg').text("");
    	$('#deletemsg').text("");
    	$('#deleteallmsg').text("");	
    }
    