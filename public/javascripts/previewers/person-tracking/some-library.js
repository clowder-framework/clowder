(function($, Configuration) {
	console.log("Person tracking previewer for " + Configuration.id);	
	
    var pathJs = window.location.protocol + "//" + window.location.hostname + (window.location.port ? ':' + window.location.port: '') + Configuration.jsPath + "/";
	
	// retrieve the metadata
    var jsRoutesObject = jsRoutes.api.Files.getTechnicalMetadataJSON(Configuration.fileid);
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
		if (data[0] == undefined)
			return;
		
        var pathFlotJS = pathJs + "jquery.flot.js";
        var pathNavigateJS = pathJs + "jquery.flot.navigate.js";
        var pathCrosshairJS = pathJs + "jquery.flot.crosshair.js";
        var pathPopcornJS = pathJs + "popcorn-complete.min.js";
        var sortedFrameDataArray = new Array();
        
        var deferredGetScript = function(url) {
            var deferred = $.Deferred();

            $.getScript( url, function() {
                deferred.notify( "deferred notify GET for " + url );
            })
            .done(function(data) {
                deferred.notify( "deferred notify done for " + url );
                deferred.resolve( data );
            })
            .fail(function(err) {
                deferred.notify( "deferred notify fail for " + url);                
                deferred.reject(err)
            });

            return deferred.promise();
        };

        $.when(
            deferredGetScript( pathFlotJS ),
            deferredGetScript( pathNavigateJS ),
            deferredGetScript( pathCrosshairJS ),
            deferredGetScript( pathPopcornJS )/*,
            $.Deferred(function( deferred ){
                $( deferred.resolve );
            })*/
        ).done(function(){
            console.log("downloaded JS sciprts");

            // Processing JSON data            
            var jsonFrameArray = data[0].result.frame;
            var jsonFrameArrayLength = jsonFrameArray.length;            
            // Pass 1: Rearrange data
            for(var i = 0; i < jsonFrameArrayLength; i++) {
                var frameIndex = parseInt(jsonFrameArray[i]["@number"]);
                var objList = jsonFrameArray[i].objectlist;                
                if(typeof(objList) == 'object' && (objList != undefined || objList != null)){

                    // When there is only one person in frame    
                    if(objList.object.length == undefined && objList.object["@id"]) {
                        var id = parseInt(objList.object["@id"]);                    
                        // if array element is not existing
                        if(sortedFrameDataArray[id-1] == undefined || sortedFrameDataArray[id-1] == null) {
                            var objPerson = new Object();
                            objPerson.label = "Person " + id;
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
                                objPerson.label = "Person " + id;
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
            
            //display video on screen and visualize person tracking
    		console.log("Updating tab " + Configuration.tab);    		
            
            //add video to display
            $(Configuration.tab).append("<br/>");
            $(Configuration.tab).append("<video width='750px' id='video' controls><source src='" + Configuration.url + "'></source></video>");
            
            // add graph div div and legend div for jQuery flot
            $(Configuration.tab).append("<div id='persontracking' style='width: 750px; height: 400px; float: left; margin-bottom: 20px; margin-top: 20px;'></div>");
    		$("#persontracking").append("<div id='placeholder' style='width: 650px; height: 400px; margin-right: 10px; float: left;'></div>");
	    	$("#persontracking").append("<div id='legend' style='margin-right: 10px; margin-top: 10px; float: left;'></div>");
            
            var totalFrames = 2000;
            var maxFrames = 300;
            var numPeople = sortedFrameDataArray.length + 1;
            var fps = 30;
            var offsetVal = 5;

            var ticksArray = new Array();

            var timeInSec = Math.ceil(totalFrames / fps);

            for (var i = 1; i <= timeInSec; i++) {
                if( i % 2 != 0)
                    ticksArray.push(i * fps);
            }
            
            function timeTickFormatter (val, axis) {
                        var hr = 0,
                            min = 0,
                            sec = 0,
                            milli = 0;
                        var fps = 30,
                            calc = 0;
                        sec = Math.floor(val / fps);
                        min = Math.floor(sec / 60);
                        sec = sec % 60;

                        hr = Math.floor(min / 60);
                        min = min % 60;
                        milli = Math.round((val % fps) * 1000 / fps);

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
                        return hr + ":" + min + ":" + sec + ":" + milli;
                        //return val;
                    }

            var options = {
                crosshair: {
                    mode: "x"
                },
                legend: {
                    container: $("#legend")
                },
                grid: {
                    show: true,
                    hoverable: false,
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
                var frameNumber = currentTime * fps
                crossHairPos = frameNumber;
                setCrossHairPosition();
            });

            //Debug code start
            //$pop.mute();
            //$pop.play();
            //Debug code end

            placeholder.bind("plotpan plotzoom", function (event, plot) {
                plot.unlockCrosshair();
                plot.setCrosshair({
                    x: crossHairPos
                })
                plot.lockCrosshair();
            });
        });
        /*.fail(function(jqxhr, settings, exception){
            console.log("Failed to load JS scripts.");
        });*/
	    
	});	
	
}(jQuery, Configuration));
