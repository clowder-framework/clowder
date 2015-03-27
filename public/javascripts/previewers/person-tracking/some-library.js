(function($, Configuration) {
	console.log("Person tracking previewer for " + Configuration.id);	
	
    var pathJs = window.location.protocol + "//" + window.location.hostname + (window.location.port ? ':' + window.location.port: '') + Configuration.jsPath + "/";
	
	// retrieve the metadata
	var metadataApiUrl = "/api/files/" + Configuration.fileid + "/technicalmetadatajson";
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

        //dowload JQuery library files        	        	        
        $.when(
            $.getScript( pathFlotJS ),
            $.getScript( pathNavigateJS ),
            $.getScript( pathCrosshairJS ),
            $.getScript( pathPopcornJS )
        ).done(function(){
        
            console.log("downloaded JS sciprts");
            
            console.log(data);
            
            //need to process JSON data
            
            var jsonFrameArray = data[0].result.frame;
            var jsonFrameArrayLength = jsonFrameArray.length;                        
            
            for(var i = 0; i < jsonFrameArrayLength; i++){
                
                var frameIndex = jsonFrameArray[i]["@number"];
                var objList = jsonFrameArray[i].objectlist;
                var objPerson = new Object();
                
               console.log(objList.length);
                
                for(var j = 0; j < objList.length; j++){
                    console.log(objList[j].object["@id"]);
                }
            }
            
            //display video on screen and visualize person tracking
    		console.log("Updating tab " + Configuration.tab);
            
            //add video to display
            $(Configuration.tab).append("<br/>");
            $(Configuration.tab).append("<video width='750px' id='video' controls><source src='" + Configuration.url + "'></source></video>");
            
            // add graph div div and legend div for jQuery flot
    		$(Configuration.tab).append("<div id='placeholder'  style='width: 650px; height: 350px; float left;'></div>");
	    	$(Configuration.tab).append("<div id='legend' style='margin-right: 10px; float left;'></div>");
            
            var totalFrames = 2000;
            var maxFrames = 300;
            var numPeople = 20;
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
                    shadowSize: 1
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
            $("#zoomout").bind("click", function (event) {
                plot.zoomOut();
            });

            /*placeholder.bind("plotzoom", function (event, plot) {
                var axes = plot.getAxes();
                $(".message").html("Zooming to x: " + axes.xaxis.min.toFixed(2) + " &ndash; " + axes.xaxis.max.toFixed(2) + " and y: " + axes.yaxis.min.toFixed(2) + " &ndash; " + axes.yaxis.max.toFixed(2));
            });*/

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
                console.log(frameNumber);
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
        
        })
        .fail(function(){
            console.log("Failed to load JS scripts.");
        });
	    
	});	
	
}(jQuery, Configuration));
