(function($, Configuration) {
	console.log("Person tracking previewer for " + Configuration.id);    
	
    var pathJs = window.location.protocol + "//" + window.location.hostname + (window.location.port ? ':' + window.location.port: '') + Configuration.jsPath + "/";
	
	// retrieve the metadata
    var jsRoutesObject = jsRoutes.api.Files.getTechnicalMetadataJSON(Configuration.id);
    var metadataApiUrl = jsRoutesObject.url;
	var request = $.ajax({
		type : "GET",
		url : metadataApiUrl,
		dataType : "json"
	});	   

	request.done(function(data) {
	    
        // if there are no technical metadata then display a message
		if (data == null)
			return;
		if (data == undefined)
			return;
		if (data.length == 0)
			return;

		var trackingMetadataIndex = -1;

        // Search metadata for person tracking
        for (var i=0; i<data.length; i++) {
            if (data[i]["person-tracking-result"] == undefined)
                continue;
            if (data[i]["person-tracking-result"] == "")
                continue;
            trackingMetadataIndex = i;
            break;
        }

        // if it couldn't find the index, display a message and return
        if (trackingMetadataIndex == -1){
            console.log("Updating tab " + Configuration.tab);
            $(Configuration.tab).append("<br/>");
            $(Configuration.tab).append('<div class="col-md-12"><h4>Sorry, invalid person tracking metadata. Preview generation failed.</h4></div>');
            return;
        }

        var pathFlotJS = pathJs + "jquery.flot.js";
        var pathNavigateJS = pathJs + "jquery.flot.navigate.js";
        var pathCrosshairJS = pathJs + "jquery.flot.crosshair.js";
        var pathPopcornJS = pathJs + "popcorn-complete.min.js";        
        var sortedFrameDataArray = new Array();
        var sortedFrameDataArrayCopy = new Array();
        var frameDataArray = new Array(); // To store array of frames obtained from the JSON response
        var frameDataArrayCopy = new Array(); // To store a copy of frameDataArray
        var labelArray = new Array(); // To store person label/ID information
        var labelArrayCopy = new Array(); // To store a copy of labelArray
        var isEditingInProgress = false;
        var hasTrackingDataChanged = false;
        
        var syncGetScript = function(url){
            var deferred = $.Deferred();

            $.ajax({
                url: url,
                dataType: "script",
                async: false,
                success: function(data){
                    deferred.notify( "deferred notify GET for " + url );                  
                }
            })
            .done(function(data) {
                deferred.notify( "deferred notify done for " + url );
                deferred.resolve( data );
            })
            .fail(function(err) {
                deferred.notify( "deferred notify fail for " + url); 
                console.log("Failed to load: " + url);
                deferred.reject(err)
            });

            return deferred.promise();
        }

        var deferredGetScript = function(url) {
            var deferred = $.Deferred();            

            $.getScript( url, function(xhr) {
                deferred.notify( "deferred notify GET for " + url );

            })
            .done(function(data) {
                deferred.notify( "deferred notify done for " + url );
                deferred.resolve( data );
            })
            .fail(function(err) {
                deferred.notify( "deferred notify fail for " + url); 
                console.log("Failed to load: " + url);
                deferred.reject(err)
            });

            return deferred.promise();
        };    

        /*var deferredFotJS = deferredGetScript( pathFlotJS );
        var deferredNavigateJS = deferredGetScript( pathNavigateJS );
        var deferredCrosshairJS = deferredGetScript( pathCrosshairJS );
        var deferredPopcornJS = deferredGetScript( pathPopcornJS );*/        

        var syncFlotJS = syncGetScript( pathFlotJS );
        var syncNavigateJS = syncGetScript( pathNavigateJS );
        var syncCrosshairJS = syncGetScript( pathCrosshairJS );
        var syncPopcornJS = syncGetScript( pathPopcornJS );

        $.when(            
            syncFlotJS,
            syncNavigateJS,
            syncCrosshairJS,
            syncPopcornJS

        ).done(function(){
            console.log("downloaded JS sciprts");

            if(data[trackingMetadataIndex]["person-tracking-result"].frame != undefined){

                // Processing JSON data            
                var jsonFrameArray = data[trackingMetadataIndex]["person-tracking-result"].frame;
                var jsonFrameArrayLength = jsonFrameArray.length;
                var videoHeight = parseInt(data[trackingMetadataIndex]["person-tracking-result"]["@video-height"]);
                var videoWidth = parseInt(data[trackingMetadataIndex]["person-tracking-result"]["@video-width"]);
                var videoFrameRate = parseInt(data[trackingMetadataIndex]["person-tracking-result"]["@frame-rate"]);
                var endFrameNumber = parseInt(data[trackingMetadataIndex]["person-tracking-result"]["@end-frame"]);

                // Pass 1: Rearrange data
                for(var i = 0; i < jsonFrameArrayLength; i++) {
                    var frameIndex = parseInt(jsonFrameArray[i]["@number"]);
                    var objList = jsonFrameArray[i].objectlist;                
                    if(typeof(objList) == 'object' && (objList != undefined || objList != null)){

                        frameDataArray[frameIndex-1] = jsonFrameArray[i];

                        // When there is only one person in frame    
                        if(objList.object.length == undefined && objList.object["@id"]) {
                            var id = parseInt(objList.object["@id"]);                    
                            // if array element is not existing
                            if(sortedFrameDataArray[id-1] == undefined || sortedFrameDataArray[id-1] == null) {
                                var objPerson = new Object();
                                objPerson.label = "Person_" + id;
                                var personFrameData = new Array();
                                personFrameData.push(new Array(frameIndex-1, id));
                                objPerson.data = personFrameData;                        
                                sortedFrameDataArray[id-1] = objPerson;                        
                            }
                            // if array element is already present
                            else {                    
                                var objPerson = sortedFrameDataArray[id-1];
                                objPerson.data.push(new Array(frameIndex-1, id));
                                sortedFrameDataArray[id-1] = objPerson;
                            }
                        }
                        // When there are multiple people in a frame
                        else if(objList.object.length > 0) {

                            for(var j=0; j< objList.object.length; j++){                            
                                var id = parseInt(objList.object[j]["@id"]);                    
                                // if array element is not existing
                                if(sortedFrameDataArray[id-1] == undefined || sortedFrameDataArray[id-1] == null) {
                                    var objPerson = new Object();
                                    objPerson.label = "Person_" + id;
                                    var personFrameData = new Array();
                                    personFrameData.push(new Array(frameIndex-1, id));
                                    objPerson.data = personFrameData;                        
                                    sortedFrameDataArray[id-1] = objPerson;                        
                                }
                                // if array element is already present
                                else {                    
                                    var objPerson = sortedFrameDataArray[id-1];
                                    objPerson.data.push(new Array(frameIndex-1, id));
                                    sortedFrameDataArray[id-1] = objPerson;
                                }
                            }
                        }                                    
                    }                
                }
                
                // Pass 2: Data reduction. 
                // e.g. if Person 1 is present in frame #1 and frame #2, delete data item for frame #2
                // if Person 1 is present till frame #50 and then continues from frame #100, insert a null in between.
                // This results in creating new horizontal bars.
                
                for(var i=0; i < sortedFrameDataArray.length; i++) {
                    // Since in JS, array is passed by reference by default, changes get reflected in sortedFrameDataArray variable too
                    var personDataArray = sortedFrameDataArray[i].data; 
                    var frameIndexCounter = 1;
                    
                    for(var startIndex = 0; startIndex < personDataArray.length;) {
                       
                       // When at least three elements are present in the array
                       if(personDataArray[startIndex + 2] != undefined ) {
                            // First two elements are in sequence
                            if(personDataArray[startIndex][0] + frameIndexCounter == personDataArray[startIndex + 1][0]) {
                                // Second and third elements are also in sequence
                                if(personDataArray[startIndex][0] + frameIndexCounter + 1 == personDataArray[startIndex + 2][0]) {
                                    // Remove the second element and upate the frame index counter
                                    personDataArray.splice(startIndex + 1,1);
                                    frameIndexCounter++;
                                }
                                // Second and third element are not in sequence
                                else {
                                    // Insert a null between second and third element. Update frame index counter, move start index
                                    personDataArray.splice(startIndex + 2,0,null);
                                    frameIndexCounter = 1;
                                    startIndex += 3; 
                                }
                            }
                            // First and second elements are not in sequence
                            else {
                                // Insert a null between first and second elements. Update start index
                                personDataArray.splice(startIndex + 1,0,null);
                                startIndex += 2;
                            }
                       }
                       // When only two or less items are remaining.
                       else {
                            // If there are two elements
                            if(personDataArray[startIndex + 1] != undefined) {
                                // Check if the two elements are in sequence
                                if(personDataArray[startIndex][0] + frameIndexCounter == personDataArray[startIndex + 1][0]) {
                                    // Insert a null at the end of the array. Not really needed. Just for logical completion of algorithm.
                                    personDataArray.splice(startIndex + 2,0,null);
                                    break;
                                }
                                // If the two elements are not in sequence
                                else {
                                    // Insert a null between the first and second elements and after the elements
                                    personDataArray.splice(startIndex + 1,0,null);
                                    personDataArray.splice(startIndex + 2,0,null);
                                    break;
                                }
                            }
                            // If there is only one element left
                            else {
                                // Insert a null after the element. Not really needed. Just for logical completion of algorithm.
                                personDataArray.splice(startIndex + 1,0,null);
                                break;
                            }
                        }    
                    }                
                }

                /* Cloning frame data arrays. These cloned arrays are used in rendering bounding boxes. 
                   This is needed for rendering the bounding even when editing of tracks is in progress and for seeking video based on label clicks.
                */
                frameDataArrayCopy = JSON.parse(JSON.stringify(frameDataArray));
                sortedFrameDataArrayCopy = JSON.parse(JSON.stringify(sortedFrameDataArray));

                // Creating the label array
                for(var i=0; i < sortedFrameDataArray.length; i++) {
                    labelArray.push(sortedFrameDataArray[i].label);
                }                
                                                        
                // Display video on screen and visualize person tracking
        		console.log("Updating tab " + Configuration.tab);    		
                
                /*  If video preview is available, display it.
                    If not, display the raw video file.
                */
                var jsRoutesObject = jsRoutes.api.Files.filePreviewsList(Configuration.id);
                var listPreviewsApiUrl = jsRoutesObject.url;
                var request = $.ajax({
                    type : "GET",
                    url : listPreviewsApiUrl,
                    dataType : "json"
                });    

                request.done(function(data) {

                    console.log("downloaded previews list");
                    var videoUrl = "";                    

                    /*  If this previewer is running, it implies that there is one preview in the list. 
                        It is the pseudo preview which is a blank XML file. So, look for the cases where more than one previews are listed.
                    */
                    if (data.length > 1 ){
                        for (var i=0; i  < data.length; i++){                    
                            // Checking for a video preview
                            if (data[i].contentType == "video/mp4" || data[i].contentType == "video/webm"){
                                var videoPreviewId = data[i].id;
                                var jsRoutesObject = jsRoutes.api.Previews.download(videoPreviewId);
                                videoUrl = jsRoutesObject.url;
                                break;                            
                            }
                        }
                    }
                    // No video preview found. Use the raw video file.
                    else {

                        videoUrl = Configuration.url;
                        videoUrl = videoUrl.replace(Configuration.fileid, Configuration.id).replace("previews","files").concat("/blob");                        
                    }                    

                    // Add video and canvas to display
                    $(Configuration.tab).append("<br/>");
                    $(Configuration.tab).append("<div id='videoDiv' style='width: 750px; position: relative; top: 0px; left: 0px;'></div>");
                    $("#videoDiv").append("<video width='750px' id='video' controls><source src='" + videoUrl + "'></source></video>");                    
                    $("#videoDiv").append("<canvas id='canvas' style='position: absolute; top: 0px; left: 0px;' ></canvas>");
                    
                    // Add graph div and legend div for jQuery flot
                    $(Configuration.tab).append("<div id='persontracking' style='width: 750px; height: 400px; float: left; margin-bottom: 20px; margin-top: 10px;'></div>");
                    $("#persontracking").append("<div id='placeholder' style='width: 560px; height: 400px; margin-right: 10px; float: left;'></div>");
                    $("#persontracking").append("<div id='legend' style='width: 150px; margin-right: 10px; margin-top: 5px; float: left;'></div>");
                    $("#persontracking").append("<span class=button-bar> <button id='btnSaveChanges' onClick='savePersonTrackingChanges(); return false;' class='usr_md_submit btn btn-default btn-xs' " + 
                                                " style='margin-right: 10px; margin-top: 5px; float: left; display:none;'>Save</button>"+
                                                "<button id='btnCancelChanges' onClick='cancelPersonTrackingChanges(); return false;' class='usr_md_submit btn btn-default btn-xs' " + 
                                                "style='margin-right: 10px; margin-top: 5px; float: left; display:none;'>Cancel</button></span>");

                    var canvas = $("#canvas");
                    var video = $("#video");
                    var context = canvas[0].getContext('2d');

                    // Display bounding boxes on canvas
                    var renderBoundingBoxes = function(frame) {

                        var series = plot.getData();
                        context.clearRect(0, 0, canvas.width(), canvas.height());                    

                        if(frameDataArrayCopy[frame-1] != null && frameDataArrayCopy[frame-1] != undefined){

                            var objList = frameDataArrayCopy[frame-1].objectlist;
                            var displayHeight = video.height();
                            var displayWidth = video.width();
                            var scaleHeight = displayHeight/videoHeight;
                            var scaleWidth = displayWidth/videoWidth;

                            if(objList.object != null && objList.object != undefined) {

                                // When there is only one person in frame    
                                if(objList.object.length == undefined && objList.object["@id"]) {
                                    var personObj = objList.object;
                                    var id = parseInt(personObj["@id"]);

                                    var xCenter = parseInt(personObj.box["@xc"]) * scaleWidth;
                                    var yCenter = parseInt(personObj.box["@yc"]) * scaleHeight;
                                    var boxWidth = parseInt(personObj.box["@w"]) * scaleWidth;
                                    var boxHeight = parseInt(personObj.box["@h"]) * scaleHeight;
                                    var personSeriesIndex = 0;

                                    for(var k=0; k< series.length; k++){
                                        // Finding the series whose ID is same as that of the current person
                                        if(personObj["@id"] == series[k].label.split("Person_")[1]){
                                            personSeriesIndex = k;
                                            break;
                                        }
                                    }
                                                        
                                    context.beginPath();
                                    context.strokeStyle = series[personSeriesIndex].color;
                                    context.lineWidth = 1.5;
                                    context.rect(xCenter - boxWidth/2, yCenter - boxHeight/2, boxWidth, boxHeight);
                                    context.stroke();
                                    context.closePath();
                                }
                                // When there are multiple people in a frame
                                else if(objList.object.length > 0) {

                                    for(var j=0; j< objList.object.length; j++){                            
                                        var personObj = objList.object[j];
                                        var id = parseInt(personObj["@id"]);                                

                                        var xCenter = parseInt(personObj.box["@xc"]) * scaleWidth;
                                        var yCenter = parseInt(personObj.box["@yc"]) * scaleHeight;
                                        var boxWidth = parseInt(personObj.box["@w"]) * scaleWidth;
                                        var boxHeight = parseInt(personObj.box["@h"]) * scaleHeight;                            

                                        for(var k=0; k< series.length; k++){
                                            //Finding the series whose ID is same as that of the current person
                                            if(personObj["@id"] == series[k].label.split("Person_")[1]){
                                                personSeriesIndex = k;
                                                break;
                                            }
                                        }
                                                            
                                        context.beginPath();
                                        context.strokeStyle = series[personSeriesIndex].color;
                                        context.lineWidth = 1.5;
                                        context.rect(xCenter - boxWidth/2, yCenter - boxHeight/2, boxWidth, boxHeight);
                                        context.stroke();
                                        context.closePath();
                                    }
                                }
                            }
                        }
                    }

                    // Change the canvas dimensions the first time
                    video.one('play', 
                        function (event) {
                            // Updating canvas width and height
                            var canvasHeight = video.height() - 35;
                            var canvasWidth = video.width();
                            canvas.attr({width:canvasWidth,height:canvasHeight});                        
                        }
                    );

                    // Displaying video through canvas
                    video.on('play', 
                        function (event) {                       
                            var $this = this; //cache                        
                            (function loop() {
                                if (!$this.paused && !$this.ended) {
                                    context.drawImage($this, 0, 0, video[0].clientWidth, video[0].clientHeight);
                                    
                                    var frameNumber = Math.floor(video[0].currentTime * videoFrameRate);
                                    renderBoundingBoxes(frameNumber);
                                    setTimeout(loop, 1000 / videoFrameRate); // drawing at current frame rate
                                }
                            })();
                        }
                    );

                    // Draw video frame and bounding boxes after seeking to a particular time
                    video.on('seeked', 
                        function (event) {
                            context.drawImage(this, 0, 0, video[0].clientWidth, video[0].clientHeight);
                            var frameNumber = Math.floor(video[0].currentTime * videoFrameRate);
                            renderBoundingBoxes(frameNumber);
                        });

                    var totalFrames = endFrameNumber;
                    var maxFrames = 300;
                    var numPeople = sortedFrameDataArray.length + 1;                
                    var offsetVal = 5;

                    var ticksArray = new Array();

                    var timeInSec = Math.ceil(totalFrames / videoFrameRate);

                    for (var i = 1; i <= timeInSec; i++) {
                        if( i % 2 != 0)
                            ticksArray.push(i * videoFrameRate);
                    }
                    
                    function timeTickFormatter (val, axis) {
                        var hr = 0,
                            min = 0,
                            sec = 0,
                            milli = 0;
                        var calc = 0;
                        sec = Math.floor(val / videoFrameRate);
                        min = Math.floor(sec / 60);
                        sec = sec % 60;

                        hr = Math.floor(min / 60);
                        min = min % 60;
                        milli = Math.round((val % videoFrameRate) * 1000 / videoFrameRate);

                        var time = "";

                        if (hr != 0) {
                            time += hr + ":";
                        }
                        if (min != 0) {
                            time += min + ":";
                        }
                        if (sec != 0) {
                            time += sec + ":";
                        }
                        if (milli != 0) {
                            time += milli
                        }
                        return hr + ":" + min + ":" + sec;// + ":" + milli;
                        //return val;
                    }

                    savePersonTrackingChanges = function() {

                        isEditingInProgress = false;
                        hasTrackingDataChanged = false;                    

                        // Re-write the global array based on the current changes.
                        sortedFrameDataArray = JSON.parse(JSON.stringify(sortedFrameDataArrayCopy));
                        labelArray = JSON.parse(JSON.stringify(labelArrayCopy));
                        frameDataArray = JSON.parse(JSON.stringify(frameDataArrayCopy));

                        $("#btnSaveChanges").hide();
                        $("#btnCancelChanges").hide();

                        // Redraw graph
                        plot.setData(sortedFrameDataArray);
                        plot.setupGrid();
                        plot.draw();
                    }

                    cancelPersonTrackingChanges = function() {

                        isEditingInProgress = false;
                        hasTrackingDataChanged = false;

                        // Copying back original array to copy array. Needed to render bounding boxes and to seek video based on label click.
                        sortedFrameDataArrayCopy = JSON.parse(JSON.stringify(sortedFrameDataArray));
                        frameDataArrayCopy = JSON.parse(JSON.stringify(frameDataArray));

                        $("#btnSaveChanges").hide();
                        $("#btnCancelChanges").hide();

                        /*Debug code. Will be deleted
                        console.log("Data before cancel");
                        console.log(sortedFrameDataArray);
                        console.log("Copy before cancel");
                        console.log(sortedFrameDataArrayCopy);*/

                        // Redraw graph
                        plot.setData(sortedFrameDataArray);
                        plot.setupGrid();
                        plot.draw();
                    }

                    saveLabel = function (oldLabel){

                        var newLabel = $("#" + oldLabel+ "Select").val();
                        var oldId = oldLabel.split("Person_")[1];
                        var newId = newLabel.split("Person_")[1];

                        // If there is a change in the label (synonymously ID) of a person
                        if(oldLabel != newLabel) {

                            // Iterate through the sorted list of persons
                            for(var i=0; i < sortedFrameDataArrayCopy.length; i++) {
                                
                                // Find the person whose label is being changed
                                if(sortedFrameDataArrayCopy[i].label == oldLabel){
                                    
                                    // Iterate through the sorted list of persons
                                    for(var j=0; j < sortedFrameDataArrayCopy.length; j++) {

                                        // If there is another person whose label matches the label of the current person which is being edited
                                        if(sortedFrameDataArrayCopy[j].label == newLabel){

                                            /* Update the data of that person by adding information about the current person.
                                               Basically merging the data of two persons into one since their labels (or IDs) match 
                                               as per the information provided by the user. */

                                            for (var k =0; k < sortedFrameDataArrayCopy[i].data.length ; k++) {
                                                if (sortedFrameDataArrayCopy[i].data[k] != null) {
                                                    sortedFrameDataArrayCopy[i].data[k][1] = parseInt(newId);
                                                }
                                            }

                                            // If the person tracks are adjacent, remove the last null value from array before appending
                                            /* TODO: Insert the bar coordinates properly so that blank spaces can be avoided.
                                            var dataIndex = sortedFrameDataArrayCopy[j].data.lastIndexOf(null) - 1;
                                            if (sortedFrameDataArrayCopy[j].data[dataIndex][0] + 1 == sortedFrameDataArrayCopy[i].data[0][0]) {
                                                sortedFrameDataArrayCopy[j].data.splice(sortedFrameDataArrayCopy[j].data.lastIndexOf(null));    
                                            }*/
                                            sortedFrameDataArrayCopy[j].data = sortedFrameDataArrayCopy[j].data.concat(sortedFrameDataArrayCopy[i].data);
                                            break;
                                        }
                                    }

                                    // Update the tracking metadata 
                                    var arrayLength = sortedFrameDataArrayCopy[i].data.length;

                                    for(var m = 0; m < arrayLength;) {

                                        if(sortedFrameDataArrayCopy[i].data[m] != null) {

                                            // Get the start and end frame indices of the current person track bar
                                            var startIndex = sortedFrameDataArrayCopy[i].data[m][0];
                                            var endIndex = sortedFrameDataArrayCopy[i].data[m+1][0];
                                            // console.log(startIndex + " " + endIndex); Debug code. Will be deleted.
                                            m += 2;                                            

                                            // Iterate through all frames in the selected range
                                            for(var frameIndex = startIndex; frameIndex <= endIndex; frameIndex++) {                                                

                                                var objList = frameDataArrayCopy[frameIndex].objectlist;

                                                // When there is only one person in frame    
                                                if(objList.object.length == undefined && objList.object["@id"]) {

                                                    if (objList.object["@id"] == oldId) {
                                                        objList.object["@id"] = newId;
                                                    }
                                                }
                                                // When there are multiple persons in frame
                                                else if(objList.object.length > 0) {

                                                    for(var j = 0; j < objList.object.length; j++){

                                                        if (objList.object[j]["@id"] == oldId) {
                                                            objList.object[j]["@id"] = newId;
                                                        }
                                                    }
                                                }
                                            }
                                        }
                                        else {

                                            m++;
                                        }
                                    }

                                    // Remove the current person from the sorted list
                                    sortedFrameDataArrayCopy.splice(i,1);
                                    break;
                                }
                            }

                            // Debug code. To delete.
                            /*console.log(sortedFrameDataArrayCopy);
                            console.log("Tracking metadata");
                            console.log(sortedFrameDataArray);*/

                            // Redraw graph
                            plot.setData(sortedFrameDataArrayCopy);
                            plot.setupGrid();
                            plot.draw();

                            var index = $.inArray(oldLabel, labelArrayCopy);
                            labelArrayCopy.splice(index,1);
                            $("#" + oldLabel + "Div").remove();

                            hasTrackingDataChanged = true;
                        }                        
                        // If there is no change in the label
                        else{

                            $("#" + oldLabel + "Div").replaceWith('<span style="margin-left: 5px;" id="'+ newLabel +'" ><a href="#" style="margin-right: 5px;">' + newLabel + '</a> ' + 
                                '<a href="#" style="margin-right: 5px;"><i class="glyphicon glyphicon-edit" onClick="editLabel(\'' + newLabel + '\'); return false;"></i></a>' + 
                                '<a href="#" style="margin-right: 5px;"><i class="glyphicon glyphicon-remove" onClick="removeLabel(\'' + newLabel + '\'); return false;"></i></a></span>');
                        }                        
                    }

                    editLabel = function (label) {

                        if (isEditingInProgress == false) {
                            isEditingInProgress = true;

                            /*Creating copy of arrays to work with.
                            Once the changes are confirmed, the original arrays are replaced by its copies. */
                            sortedFrameDataArrayCopy = JSON.parse(JSON.stringify(sortedFrameDataArray));
                            labelArrayCopy = JSON.parse(JSON.stringify(labelArray));
                            frameDataArrayCopy = JSON.parse(JSON.stringify(frameDataArray));

                            $("#btnSaveChanges").show();
                            $("#btnCancelChanges").show();
                        }

                        $("#" + label).replaceWith('<span style="margin-left: 5px;" id="'+ label + "Div" + '"><select id="'+ label + 
                            "Select" + '"></select> <button type="button" onclick="saveLabel(\'' + label + '\');">Save</button></span');
                        for(var i=0; i < labelArrayCopy.length; i++) {                    
                            $("#" + label+ "Select").append('<option value="' + labelArrayCopy[i] + '">'+ labelArrayCopy[i] +'</option>');
                        }
                        $("#" + label + "Select").val(label);
                    }

                    removeLabel = function (oldLabel) {

                        if (isEditingInProgress == false) {
                            isEditingInProgress = true;

                            /*Creating copy of arrays to work with.
                            Once the changes are confirmed, the original arrays are replaced by its copies. */
                            sortedFrameDataArrayCopy = JSON.parse(JSON.stringify(sortedFrameDataArray));
                            labelArrayCopy = JSON.parse(JSON.stringify(labelArray));
                            frameDataArrayCopy = JSON.parse(JSON.stringify(frameDataArray));

                            $("#btnSaveChanges").show();
                            $("#btnCancelChanges").show();
                        }                        

                        var oldId = oldLabel.split("Person_")[1];

                        // Iterate through the sorted list of persons
                        for(var i=0; i < sortedFrameDataArrayCopy.length; i++) {
                            
                            // Find the person whose label is being changed
                            if(sortedFrameDataArrayCopy[i].label == oldLabel){

                                // Update the tracking metadata
                                var arrayLength = sortedFrameDataArrayCopy[i].data.length;

                                for(var m = 0; m < arrayLength;) {

                                    if(sortedFrameDataArrayCopy[i].data[m] != null) {

                                        // Get the start and end frame indices of the current person track bar
                                        var startIndex = sortedFrameDataArrayCopy[i].data[m][0];
                                        var endIndex = sortedFrameDataArrayCopy[i].data[m+1][0];                                        
                                        m += 2;                                            

                                        // Iterate through all frames in the selected range
                                        for(var frameIndex = startIndex; frameIndex <= endIndex; frameIndex++) {                                                

                                            var objList = frameDataArrayCopy[frameIndex].objectlist;

                                            // When there is only one person in frame    
                                            if(objList.object.length == undefined && objList.object["@id"]) {

                                                if (objList.object["@id"] == oldId) {
                                                    objList.object = null;
                                                }
                                            }
                                            // When there are multiple persons in frame
                                            else if(objList.object.length > 0) {

                                                for(var j = 0; j < objList.object.length; j++){

                                                    if (objList.object[j]["@id"] == oldId) {
                                                        objList.object.splice(j,1);
                                                    }
                                                }
                                            }
                                        }
                                    }
                                    else {

                                        m++;
                                    }
                                }

                                // Remove the current person from the sorted list
                                sortedFrameDataArrayCopy.splice(i,1);
                                break;
                            }
                        }

                        // Debug code. To delete.
                        /*console.log(sortedFrameDataArrayCopy);
                        console.log("Tracking metadata");
                        console.log(frameDataArray);*/

                        // Redraw graph
                        plot.setData(sortedFrameDataArrayCopy);
                        plot.setupGrid();
                        plot.draw();

                        var index = $.inArray(oldLabel, labelArrayCopy);
                        labelArrayCopy.splice(index,1);
                        $("#" + oldLabel + "Div").remove();

                        hasTrackingDataChanged = true;
                    }

                    labelClicked = function(label) {
                        
                        // Seek video
                        // Iterate through the sorted list of persons
                        for(var i=0; i < sortedFrameDataArrayCopy.length; i++) {
                            
                            // Find the person whose label was clicked
                            if(sortedFrameDataArrayCopy[i].label == label){
                                
                                var arrayLength = sortedFrameDataArrayCopy[i].data.length;

                                for(var m = 0; m < arrayLength;) {

                                    if(sortedFrameDataArrayCopy[i].data[m] != null) {
                                        // Get the start and end frame indices of the current person track bar                                        
                                        var startIndex = sortedFrameDataArrayCopy[i].data[m][0];
                                        var endIndex = sortedFrameDataArrayCopy[i].data[m+1][0];
                                        video[0].currentTime = startIndex / videoFrameRate; // Set the current video position
                                        break;
                                    }
                                }
                                break;
                            }
                        }                    
                    }

                    var labelHoverIn = function(){
                        $(this).find(".glyphicon").fadeIn(100);
                    }

                    var labelHoverOut = function(){
                        $(this).find(".glyphicon").fadeOut(100);
                    }

                    var formatLabel = function (label, series){
                        return  '<span style="margin-left: 5px;" id="'+ label +'" >'+
                                    '<a href="javascript:void(0);" onClick="labelClicked(\'' + label + '\'); return false;" style="margin-right: 5px;">' + label + '</a> ' + 
                                    '<a href="javascript:void(0);" style="margin-right: 5px;">' +
                                        '<i class="glyphicon glyphicon-edit" onClick="editLabel(\'' + label + '\'); return false;"></i></a>' + 
                                    '<a href="javascript:void(0);" style="margin-right: 5px;">' +
                                        '<i class="glyphicon glyphicon-remove" onClick="removeLabel(\'' + label + '\'); return false;"></i></a></span>';
                    }

                    var options = {
                        crosshair: {
                            mode: "x"
                        },
                        legend: {
                            container: $("#legend"),
                            labelFormatter: formatLabel
                        },
                        grid: {
                            show: true,
                            hoverable: false,
                            clickable: true,
                            color: "#5E5E5E"
                        },
                        series: {
                            lines: {
                                show: true,
                                lineWidth: 10
                            },
                            shadowSize: 2
                        },
                        xaxis: {
                            axisLabel: "Time",
                            axisLabelUseCanvas: true,            
                            axisLabelFontSizePixels: 12,
                            axisLabelFontFamily: 'Verdana, Arial',
                            axisLabelPadding: 10,
                            tickColor: "#EDEDED",
                            //ticks: ticksArray,
                            minTickSize: 1,
                            tickDecimals: 0,
                            min: 0,
                            max: maxFrames,
                            autoscaleMargin: 0.05,
                            show: true,
                            zoomRange: [0, totalFrames],
                            panRange: [0, totalFrames],
                            tickFormatter: timeTickFormatter
                        },
                        yaxis: {
                            axisLabel: "Persons",
                            axisLabelUseCanvas: true,            
                            axisLabelFontSizePixels: 12,
                            axisLabelFontFamily: 'Verdana, Arial',
                            axisLabelPadding: 10,
                            tickColor: "#EDEDED",
                            minTickSize: 1,
                            tickDecimals: 0,
                            min: 0,
                            max: numPeople,
                            autoscaleMargin: 0.05,
                            show: true,
                            zoomRange: [0, numPeople],
                            panRange: [0, numPeople]
                        },
                        zoom: {
                            interactive: true
                        },
                        pan: {
                            interactive: true
                        }
                    }

                    var placeholder = $("#placeholder");
                    plot = $.plot(placeholder, sortedFrameDataArray, options);
                    //$(".legendLabel").hover(labelHoverIn, labelHoverOut);                    

                    panPlot = function () {
                        plot.getOptions().xaxes[0].min += offsetVal;
                        plot.getOptions().xaxes[0].max += offsetVal;
                        plot.setupGrid();
                        plot.draw();
                    }

                    crossHairPos = 0;
                    setCrossHairPosition = function () {
                        if (crossHairPos >= plot.getAxes().xaxis.max || crossHairPos >= totalFrames) {
                            if (plot.getAxes().xaxis.max <= totalFrames - offsetVal) {
                                panPlot();
                            } else {
                                plot.unlockCrosshair();
                                return;
                            }
                        }
                        plot.unlockCrosshair();
                        plot.setCrosshair({
                            x: crossHairPos
                        })
                        plot.lockCrosshair();
                    }

                    // create a popcorn instance
                    var $pop = Popcorn("#video");
                    $pop.on("timeupdate", function () {
                        var currentTime = this.currentTime();
                        var frameNumber = currentTime * videoFrameRate
                        crossHairPos = frameNumber;
                        setCrossHairPosition();
                    });

                    placeholder.bind("plotpan plotzoom", function (event, plot) {
                        plot.unlockCrosshair();
                        plot.setCrosshair({
                            x: crossHairPos
                        })
                        plot.lockCrosshair();
                    });
                    
                    placeholder.bind("plotclick", function (event, pos, item) {
                        //Debug code. Will be deleted.                        
                        if (item) {
                            //plot.highlight(item.series, item.datapoint);
                            var timeClicked = item.series.xaxis.ticks[item.dataIndex].label;
                            console.log(item);
                        }
                    });
                })
                .fail(function(jqxhr){
                    console.log("Failed to get previews list and hence failed to create the visualization.");            
                    console.log("Updating tab " + Configuration.tab);
                    $(Configuration.tab).append("<br/>");
                    $(Configuration.tab).append('<div class="col-md-12"><h4>Sorry, Video dowloading failed. Please refresh and try again.</h4></div>');
                });
                
            }
            else{
                console.log("Updating tab " + Configuration.tab);
                $(Configuration.tab).append("<br/>");
                $(Configuration.tab).append('<div class="col-md-12"><h4>Sorry, invalid person tracking metadata. Preview generation failed.</h4></div>');
            }
        })
        .fail(function(jqxhr){
            console.log("Failed to load JS scripts.");            
            console.log("Updating tab " + Configuration.tab);
            $(Configuration.tab).append("<br/>");
            $(Configuration.tab).append('<div class="col-md-12"><h4>Sorry, data loading failed. Please refresh and try again.</h4></div>');
        });	    
	});
	
}(jQuery, Configuration));
