//ANNOTATIONS FUNCTIONS
function clearConfigTabAnnotations(prNum){
  	  $(window["configs" + prNum].tab + " > textarea[data-annotation]").remove();
  	  $(window["configs" + prNum].tab + " > button[data-annotation]").remove(); 
  	  $(window["configs" + prNum].tab + " > h5[data-annotation]").remove();
  	  $(window["configs" + prNum].tab + " > span[data-annotation]").remove();
  	  $(window["configs" + prNum].tab + " > br[data-annotation]").remove();
    }  
    
  function focusOnAnnotation(x_coord, y_coord, z_coord, event, prNum){	
  	if(window["showAnnotations" + prNum] && (window["isCurrentSubmitted" + prNum] || (x_coord != window["currentAnnotation" + prNum][0] || y_coord != window["currentAnnotation" + prNum][1] || z_coord != window["currentAnnotation" + prNum][2]))){  	  
  	  if(window["currentAnnotation" + prNum].length == 3){
  		  if(window["isCurrentSubmitted" + prNum])
  			  $("#x3dElement" + prNum + " > scene > transform[data-annotation][translation='" + window["currentAnnotation" + prNum][0] + "," + window["currentAnnotation" + prNum][1] + "," + window["currentAnnotation" + prNum][2] 
  			  + "'] > shape > appearance > material").get(0).setAttribute("diffuseColor", "mediumblue");
  		  else{
  			  $("#x3dElement" + prNum + " > scene > transform[data-annotation][translation='" + window["currentAnnotation" + prNum][0] + "," + window["currentAnnotation" + prNum][1] + "," + window["currentAnnotation" + prNum][2] + "']").remove();
  			  window["isCurrentSubmitted" + prNum] = true;
  		  }   
  		  clearConfigTabAnnotations(prNum);		  
  	  }	  
  	  window["currentAnnotation" + prNum][0] = x_coord;
  	  window["currentAnnotation" + prNum][1] = y_coord;
  	  window["currentAnnotation" + prNum][2] = z_coord;
  	  
  	  $("#x3dElement" + prNum + " > scene > transform[data-annotation][translation='" + window["currentAnnotation" + prNum][0] + "," + window["currentAnnotation" + prNum][1] + "," + window["currentAnnotation" + prNum][2] 
  	  + "'] > shape > appearance > material").get(0).setAttribute("diffuseColor", "olivedrab");
  	  
        var annotationHeading = document.createElement('h5');
        annotationHeading.innerHTML = "Annotation description";
        annotationHeading.setAttribute('data-annotation','true');
        $(window["configs" + prNum].tab).append(annotationHeading);

        var annotationDescription = document.createElement('span');
        annotationDescription.innerHTML = $("#x3dElement" + prNum + " > scene > transform[data-annotation][translation='" + window["currentAnnotation" + prNum][0] + "," + window["currentAnnotation" + prNum][1] + "," + window["currentAnnotation" + prNum][2] + "'] > .annotationdescription").get(0).innerHTML;
        annotationDescription.setAttribute("style","display:block; width:" + window["width" + prNum] + "px;");
        annotationDescription.setAttribute('data-annotation','true');
        $(window["configs" + prNum].tab).append(annotationDescription);

        var annotationBreak = document.createElement('br');
        annotationBreak.setAttribute('data-annotation','true');
        $(window["configs" + prNum].tab).append(annotationBreak);

        var annotationEdit = document.createElement('button'); 	
        annotationEdit.setAttribute('type','button');
        annotationEdit.className = annotationEdit.className + " btn";
        annotationEdit.setAttribute("onclick","editAnnotationDescription('" + prNum + "')");
        annotationEdit.innerHTML = 'Edit';
        annotationEdit.setAttribute("style","margin-bottom: 5px;");
        annotationEdit.setAttribute('data-annotation','true');
        $(window["configs" + prNum].tab).append(annotationEdit);
        
        event.cancelBubble = true;
        event.stopPropagation();
      }
    }   
  	
    function createAnnotationPoint(x_coord, y_coord, z_coord, descriptionText, prNum){
  	  window["annotationsCount"+prNum]++; 

  	  var annotation = document.createElement('transform');
  	  annotation.setAttribute("id", "annotation_" + window["annotationsCount"+prNum]);
  	  annotation.setAttribute("DEF", "annot_" + window["annotationsCount"+prNum]);
  	  annotation.setAttribute("scale", ".15 .15 .15");
  	  annotation.setAttribute("render", "true");
  	  annotation.setAttribute("bboxCenter", "0,0,0");
  	  annotation.setAttribute("bboxSize", "-1,-1,-1");
  	  annotation.setAttribute("center", "0,0,0");
  	  annotation.setAttribute("translation", x_coord + "," + y_coord + "," + z_coord);
  	  annotation.setAttribute("rotation", "0,0,0,0");
  	  annotation.setAttribute("scaleOrientation", "0,0,0,0");
  	  annotation.setAttribute("data-annotation", "true");  
  	  document.getElementById("x3dElement" + prNum).getElementsByTagName('scene')[0].appendChild(annotation);  
  	  
  	  var annotationShape = document.createElement('shape');
  	  annotationShape.setAttribute("render", "true");
  	  annotationShape.setAttribute("bboxCenter", "0,0,0");
  	  annotationShape.setAttribute("bboxSize", "-1,-1,-1");
  	  annotationShape.setAttribute("isPickable", "false");
  	  annotation.appendChild(annotationShape);
  	  
  	  var appearance = document.createElement('appearance');
  	  appearance.setAttribute("sortType", "auto");
  	  annotationShape.appendChild(appearance);
  	  
  	  var material = document.createElement('material');
  	  material.setAttribute("diffuseColor", "mediumblue");
  	  material.setAttribute("specularColor", "peachpuff");
  	  material.setAttribute("ambientIntensity", "0.2");
  	  material.setAttribute("emissiveColor", "0,0,0");
  	  material.setAttribute("shininess", "0.2");
  	  if(window["showAnnotations"+prNum])
  		  material.setAttribute("transparency", "0");
  	  else
  		  material.setAttribute("transparency", "1");	  
  	  appearance.appendChild(material);
  	  
  	  var sphere = document.createElement('sphere');
  	  sphere.setAttribute("solid", "true");
  	  sphere.setAttribute("ccw", "true");
  	  sphere.setAttribute("useGeoCache", "true");
  	  sphere.setAttribute("radius", "0.06");  
  	  sphere.setAttribute("subdivision", "24,24");
  	  annotationShape.appendChild(sphere);
  	  
  	  var description = document.createElement('span');
  	  description.setAttribute("style","display:none;");
  	  description.classList.add('annotationdescription');
  	  description.innerHTML = descriptionText;
  	  annotation.appendChild(description);  	  
    }
    
    function submitAnnotationEdit(prNum){	  
  	  var annotation = {};
  	  annotation['x_coord'] = "" + window["currentAnnotation" + prNum][0];
  	  annotation['y_coord'] = "" + window["currentAnnotation" + prNum][1];
  	  annotation['z_coord'] = "" + window["currentAnnotation" + prNum][2];
  	  annotation['description'] = $(window["configs" + prNum].tab + " > textarea[data-annotation]").get(0).value;
  	  
  	  var request = $.ajax({
  	       type: 'POST',
  	       async:false,
  	       url: window["configs" + prNum].annotationsEditPath,
  	       data: JSON.stringify(annotation),
  	       contentType: "application/json",
  	     });
  	  
  		  request.done(function (response, textStatus, jqXHR){
  		        console.log("Response " + response);		        
  		        $("#x3dElement" + prNum + " > scene > transform[data-annotation][translation='" + window["currentAnnotation" + prNum][0] + "," + window["currentAnnotation" + prNum][1] + "," + window["currentAnnotation" + prNum][2] + "'] > .annotationdescription").get(0).innerHTML = $(window["configs" + prNum].tab + " > textarea[data-annotation]").get(0).value;

  		        clearConfigTabAnnotations(prNum);
  		  	  	
  		        var newAnnotationHeading = document.createElement('h5');
  		        newAnnotationHeading.innerHTML = "Annotation description";
  		        newAnnotationHeading.setAttribute('data-annotation','true');
  		        $(window["configs" + prNum].tab).append(newAnnotationHeading);

  		        var newAnnotationDescription = document.createElement('span');
  		        newAnnotationDescription.innerHTML = annotation['description'];
  		        newAnnotationDescription.setAttribute("style","display:block; width:" + window["width" + prNum] + "px;");
  		        newAnnotationDescription.setAttribute('data-annotation','true');
  		        $(window["configs" + prNum].tab).append(newAnnotationDescription);

  		        var annotationBreak = document.createElement('br');
  		        annotationBreak.setAttribute('data-annotation','true');
  		        $(window["configs" + prNum].tab).append(annotationBreak);

  		        var newAnnotationEdit = document.createElement('button'); 	
  		        newAnnotationEdit.setAttribute('type','button');
  		        newAnnotationEdit.className = newAnnotationEdit.className + " btn";
  		        newAnnotationEdit.setAttribute("onclick","editAnnotationDescription('" + prNum + "')");
  		        newAnnotationEdit.innerHTML = 'Edit';
  		        newAnnotationEdit.setAttribute("style","margin-bottom: 5px;");
  		        newAnnotationEdit.setAttribute('data-annotation','true');
  		        $(window["configs" + prNum].tab).append(newAnnotationEdit);	   
  		  	    		        
  			});
  		 
  		  request.fail(function (jqXHR, textStatus, errorThrown){
  			console.error(
  	 		"The following error occured: "+
  	 		textStatus, errorThrown		            
  				);
  			alert("ERROR: " + errorThrown +". Annotation edit not submitted." );
  			});
    }
    
    function cancelAnnotationEdit(prNum){
  	  	clearConfigTabAnnotations(prNum);
  	  
        var annotationHeading = document.createElement('h5');
        annotationHeading.innerHTML = "Annotation description";
        annotationHeading.setAttribute('data-annotation','true');
        $(window["configs" + prNum].tab).append(annotationHeading);  

        var annotationDescription = document.createElement('span');
        annotationDescription.innerHTML = $("#x3dElement" + prNum + " > scene > transform[data-annotation][translation='" + window["currentAnnotation" + prNum][0] + "," + window["currentAnnotation" + prNum][1] + "," + window["currentAnnotation" + prNum][2] + "'] > .annotationdescription").get(0).innerHTML;
        annotationDescription.setAttribute("style","display:block; width:" + window["width" + prNum] + "px;");
        annotationDescription.setAttribute('data-annotation','true');
        $(window["configs" + prNum].tab).append(annotationDescription);

        var annotationBreak = document.createElement('br');
        annotationBreak.setAttribute('data-annotation','true');
        $(window["configs" + prNum].tab).append(annotationBreak);

        var annotationEdit = document.createElement('button'); 	
        annotationEdit.setAttribute('type','button');
        annotationEdit.className = annotationEdit.className + " btn";
        annotationEdit.setAttribute("onclick","editAnnotationDescription('" + prNum + "')");
        annotationEdit.innerHTML = 'Edit';
        annotationEdit.setAttribute("style","margin-bottom: 5px;");
        annotationEdit.setAttribute('data-annotation','true');
        $(window["configs" + prNum].tab).append(annotationEdit);	
    }
    
    function editAnnotationDescription(prNum){
      clearConfigTabAnnotations(prNum);
  	  
  	  var editAnnotationHeading = document.createElement('h5');
  	  editAnnotationHeading.innerHTML = "Edit annotation description";
  	  editAnnotationHeading.setAttribute('data-annotation','true');
  	  $(window["configs" + prNum].tab).append(editAnnotationHeading);
  	  
  	  var editAnnotationTextBox = document.createElement('textarea');
  	  editAnnotationTextBox.setAttribute("rows", "4");
  	  editAnnotationTextBox.setAttribute("style","width:" + (window["width" + prNum] - 15) + "px;");
  	  editAnnotationTextBox.value = $("#x3dElement" + prNum + " > scene > transform[data-annotation][translation='" + window["currentAnnotation" + prNum][0] + "," + window["currentAnnotation" + prNum][1] + "," + window["currentAnnotation" + prNum][2] + "'] > .annotationdescription").get(0).innerHTML;
  	  editAnnotationTextBox.setAttribute('data-annotation','true');
  	  $(window["configs" + prNum].tab).append(editAnnotationTextBox);
  	  
  	  var annotationBreak = document.createElement('br');
  	  annotationBreak.setAttribute('data-annotation','true');
  	  $(window["configs" + prNum].tab).append(annotationBreak);
  	  
  	  var editAnnotationSubmit = document.createElement('button'); 	
  	  editAnnotationSubmit.setAttribute('type','button');
  	  editAnnotationSubmit.className = editAnnotationSubmit.className + " btn";
  	  editAnnotationSubmit.setAttribute('style','margin-right:10px;');
  	  editAnnotationSubmit.setAttribute("onclick","submitAnnotationEdit('" + prNum + "')");
  	  editAnnotationSubmit.innerHTML = 'OK';
  	  editAnnotationSubmit.setAttribute("style","margin-bottom: 5px;");
  	  editAnnotationSubmit.setAttribute('data-annotation','true');
  	  $(window["configs" + prNum].tab).append(editAnnotationSubmit);
  	  
  	  var editAnnotationCancel = document.createElement('button'); 	
  	  editAnnotationCancel.setAttribute('type','button');
  	  editAnnotationCancel.className = editAnnotationCancel.className + " btn";
  	  editAnnotationCancel.setAttribute("onclick","cancelAnnotationEdit('" + prNum + "')");
  	  editAnnotationCancel.innerHTML = 'Cancel';
  	  editAnnotationCancel.setAttribute("style","margin-bottom: 5px;");
  	  editAnnotationCancel.setAttribute('data-annotation','true');
  	  $(window["configs" + prNum].tab).append(editAnnotationCancel);
  	  
    }
    
    function submitAnnotation(prNum){
  	  var annotation = {};
  	  annotation['x_coord'] = "" + window["currentAnnotation" + prNum][0];
  	  annotation['y_coord'] = "" + window["currentAnnotation" + prNum][1];
  	  annotation['z_coord'] = "" + window["currentAnnotation" + prNum][2];
  	  annotation['description'] = $(window["configs" + prNum].tab + " > textarea").get(0).value;
  	  var request = $.ajax({
  	       type: 'POST',
  	       async:false,
  	       url: window["configs" + prNum].annotationsAttachPath,
  	       data: JSON.stringify(annotation),
  	       contentType: "application/json",
  	     });    

  		  request.done(function (response, textStatus, jqXHR){
  		        console.log("Response " + response);		        
  		        $("#x3dElement" + prNum + " > scene > transform[data-annotation][translation='" + window["currentAnnotation" + prNum][0] + "," + window["currentAnnotation" + prNum][1] + "," + window["currentAnnotation" + prNum][2] + "'] > .annotationdescription").get(0).innerHTML = $(window["configs" + prNum].tab + " > textarea[data-annotation]").get(0).value;
  		        window["isCurrentSubmitted" + prNum] = true;
  		        
  		        clearConfigTabAnnotations(prNum);
  		  	  	
  		  	    if(window["showAnnotations" + prNum]){
  		  		  var newAnnotationHeading = document.createElement('h5');
  		  		  newAnnotationHeading.innerHTML = "Annotation description";
  		  		  newAnnotationHeading.setAttribute('data-annotation','true');
  		  		  $(window["configs" + prNum].tab).append(newAnnotationHeading);
  		  		  
  		  		  var newAnnotationDescription = document.createElement('span');
  		  		  newAnnotationDescription.innerHTML = annotation['description'];
  		  		  newAnnotationDescription.setAttribute("style","display:block; width:" + window["width" + prNum] + "px;");
  		  		  newAnnotationDescription.setAttribute('data-annotation','true');
  		  		  $(window["configs" + prNum].tab).append(newAnnotationDescription);
  		  		  
  		  		  var annotationBreak = document.createElement('br');
  		  		  annotationBreak.setAttribute('data-annotation','true');
  		  		  $(window["configs" + prNum].tab).append(annotationBreak);
  		  		  
  		  		  var newAnnotationEdit = document.createElement('button'); 	
  		  		  newAnnotationEdit.setAttribute('type','button');
  		  		  newAnnotationEdit.className = newAnnotationEdit.className + " btn";
  		  		  newAnnotationEdit.setAttribute("onclick","editAnnotationDescription('" + prNum + "')");
  		  		  newAnnotationEdit.innerHTML = 'Edit';
  		  		  newAnnotationEdit.setAttribute("style","margin-bottom: 5px;");
  		  		  newAnnotationEdit.setAttribute('data-annotation','true');
  		  		  $(window["configs" + prNum].tab).append(newAnnotationEdit);	
  		  	    }else{
  		  	    	$("#x3dElement" + prNum + " > scene > transform[data-annotation][translation='" + window["currentAnnotation" + prNum][0] + "," + window["currentAnnotation" + prNum][1] + "," + window["currentAnnotation" + prNum][2] 
  		  	    	+ "'] > shape > appearance > material").get(0).setAttribute("diffuseColor", "mediumblue");
  		  	    	window["currentAnnotation" + prNum] = new Array();
  		  	    }		        
  			});   
  		 
  		  request.fail(function (jqXHR, textStatus, errorThrown){
     		console.error(
         		"The following error occured: "+
         		textStatus, errorThrown		            
     			);
     		alert("ERROR: " + errorThrown +". Annotation not submitted." );
  			});	  
    }   
    
    function cancelNewAnnotation(prNum){
  	  $("#x3dElement" + prNum + " > scene > transform[data-annotation][translation='" + window["currentAnnotation" + prNum][0] + "," + window["currentAnnotation" + prNum][1] + "," + window["currentAnnotation" + prNum][2] + "']").remove();
  	  window["isCurrentSubmitted" + prNum] = true;
  	  window["currentAnnotation" + prNum] = new Array();
  	  clearConfigTabAnnotations(prNum);	  
    }
  	
    function addAnnotation(event, prNum){
    	clearConfigTabAnnotations(prNum);

    	if(window["currentAnnotation" + prNum].length == 3)
    		if(window["isCurrentSubmitted" + prNum])
    			$("#x3dElement" + prNum + " > scene > transform[data-annotation][translation='" + window["currentAnnotation" + prNum][0] + "," + window["currentAnnotation" + prNum][1] + "," + window["currentAnnotation" + prNum][2] 
    			+ "'] > shape > appearance > material").get(0).setAttribute("diffuseColor", "mediumblue");
    		else{
    			$("#x3dElement" + prNum + " > scene > transform[data-annotation][translation='" + window["currentAnnotation" + prNum][0] + "," + window["currentAnnotation" + prNum][1] + "," + window["currentAnnotation" + prNum][2] + "']").remove();
    			isCurrentSubmitted = true;
    		}

    	window["currentAnnotation" + prNum][0] = event.hitPnt[0];
    	window["currentAnnotation" + prNum][1] = event.hitPnt[1];
    	window["currentAnnotation" + prNum][2] = event.hitPnt[2];

    	createAnnotationPoint(window["currentAnnotation" + prNum][0], window["currentAnnotation" + prNum][1], window["currentAnnotation" + prNum][2], "", prNum);

    	$("#x3dElement" + prNum + " > scene > transform[data-annotation][translation='" + window["currentAnnotation" + prNum][0] + "," + window["currentAnnotation" + prNum][1] + "," + window["currentAnnotation" + prNum][2] 
    	+ "'] > shape > appearance > material").get(0).setAttribute("diffuseColor", "olivedrab");

    	window["isCurrentSubmitted" + prNum] = false;

    	var newAnnotationHeading = document.createElement('h5');
    	newAnnotationHeading.innerHTML = "Enter annotation description";
    	newAnnotationHeading.setAttribute('data-annotation','true');
    	$(window["configs" + prNum].tab).append(newAnnotationHeading);

    	var newAnnotationTextBox = document.createElement('textarea');
    	newAnnotationTextBox.setAttribute("rows", "4");
    	newAnnotationTextBox.setAttribute("style","width:" + (window["width" + prNum] - 15) + "px;");
    	newAnnotationTextBox.setAttribute('data-annotation','true');
    	$(window["configs" + prNum].tab).append(newAnnotationTextBox);

    	var annotationBreak = document.createElement('br');
    	annotationBreak.setAttribute('data-annotation','true');
    	$(window["configs" + prNum].tab).append(annotationBreak);

    	var newAnnotationSubmit = document.createElement('button'); 	
    	newAnnotationSubmit.setAttribute('type','button');
    	newAnnotationSubmit.className = newAnnotationSubmit.className + " btn";
    	newAnnotationSubmit.setAttribute('style','margin-right:10px;');
    	newAnnotationSubmit.setAttribute("onclick","submitAnnotation('" + prNum + "')");
    	newAnnotationSubmit.innerHTML = 'OK';
    	newAnnotationSubmit.setAttribute("style","margin-bottom: 5px;");
    	newAnnotationSubmit.setAttribute('data-annotation','true');
    	$(window["configs" + prNum].tab).append(newAnnotationSubmit);

    	var newAnnotationCancel = document.createElement('button'); 	
    	newAnnotationCancel.setAttribute('type','button');
    	newAnnotationCancel.className = newAnnotationCancel.className + " btn";
    	newAnnotationCancel.setAttribute("onclick","cancelNewAnnotation('" + prNum + "')");
    	newAnnotationCancel.innerHTML = 'Cancel annotation addition';
    	newAnnotationCancel.setAttribute("style","margin-bottom: 5px;");
    	newAnnotationCancel.setAttribute('data-annotation','true');
    	$(window["configs" + prNum].tab).append(newAnnotationCancel);

    	event.cancelBubble = true;
    	event.stopPropagation();
    }
    
  //MEASURING FUNCTIONS
    function startMeasuring(event, prNum){
		 window["measuringMode" + prNum] = true;
		 
		 $("#measuringlinetrafo").remove();
		 $("#measuringvalue").each(function(){
			 $(this).remove();
			 $("#x3dElementTable" + prNum).css("margin-bottom","-=20");
	    	});
		 	 
		 window["currentMeasureStart" + prNum][0] = event.hitPnt[0];
		 window["currentMeasureStart" + prNum][1] = event.hitPnt[1];
		 window["currentMeasureStart" + prNum][2] = event.hitPnt[2];
		 
		 var measuringLineTrafo = document.createElement('transform');
		 measuringLineTrafo.setAttribute("id", "measuringlinetrafo"+prNum);
		 measuringLineTrafo.setAttribute("DEF", "measuring_line_trafo"+prNum);
		 measuringLineTrafo.setAttribute("translation", "0 0 0");
		 measuringLineTrafo.setAttribute("bboxCenter", "0,0,0");
		 measuringLineTrafo.setAttribute("bboxSize", "-1,-1,-1");
		 measuringLineTrafo.setAttribute("data-measuring", "true");  
		 
		 
		 var measuringLineShape = document.createElement('shape');
		 measuringLineShape.setAttribute("id", "measuringlineshape"+prNum);
		 measuringLineShape.setAttribute("DEF", "measuring_line_shape"+prNum);
		 measuringLineShape.setAttribute("isPickable", "false");
		 measuringLineTrafo.appendChild(measuringLineShape);
		 
		 var measuringLineAppearance = document.createElement('appearance');
		 measuringLineAppearance.setAttribute("id", "measuringlineappearance"+prNum);
		 measuringLineAppearance.setAttribute("DEF", "measuring_line_appearance"+prNum);
		 measuringLineShape.appendChild(measuringLineAppearance);
		 
		 var measuringLineMaterial = document.createElement('material');
		 measuringLineMaterial.setAttribute("id", "measuringlinematerial"+prNum);
		 measuringLineMaterial.setAttribute("DEF", "measuring_line_material"+prNum);
		 measuringLineMaterial.setAttribute("emissiveColor", "1 0 0");
		 measuringLineMaterial.setAttribute("diffuseColor", "0 0 0");
		 measuringLineAppearance.appendChild(measuringLineMaterial);
		 
		 var measuringLine = document.createElement('indexedLineSet');
		 measuringLine.setAttribute("id", "measuringline"+prNum);
		 measuringLine.setAttribute("DEF", "measuring_line"+prNum);
		 measuringLine.setAttribute("coordIndex", "0 1 -1");
		 measuringLineShape.appendChild(measuringLine);
		 
		 var measuringLineCoords = document.createElement('coordinate');
		 measuringLineCoords.setAttribute("id", "measuringlinecoordinate"+prNum);
		 measuringLineCoords.setAttribute("DEF", "measuring_line_coordinate"+prNum);
		 measuringLineCoords.setAttribute("point", window["currentMeasureStart" + prNum][0] + " " + window["currentMeasureStart" + prNum][1] + " " + window["currentMeasureStart" + prNum][2]
		 									+ ", " + window["currentMeasureStart" + prNum][0] + " " + window["currentMeasureStart" + prNum][1] + " " + window["currentMeasureStart" + prNum][2]);		 
		 measuringLine.appendChild(measuringLineCoords);
		 
//		 var measuringLineColor = document.createElement('color');
//		 measuringLineColor.setAttribute("id", "measuringlinecolor");
//		 measuringLineColor.setAttribute("DEF", "measuring_line_color");
//		 measuringLineColor.setAttribute("color", "1 0 0, 1 0 0");
//		 measuringLine.appendChild(measuringLineColor);
		 
		 document.getElementById("x3dElement" + prNum).getElementsByTagName('scene')[0].appendChild(measuringLineTrafo);
		 
		 $("#x3dElement" + prNum).get(0).style.cursor = "crosshair";
		  
		 event.cancelBubble = true;
	     event.stopPropagation();
    }
    
    function calculateDistance(event, prNum){
    	var distanceValue = round_number(Math.sqrt(Math.pow(window["currentMeasureStart" + prNum][0]-event.hitPnt[0],2)+Math.pow(window["currentMeasureStart" + prNum][1]-event.hitPnt[1],2)
    						+Math.pow(window["currentMeasureStart" + prNum][2]-event.hitPnt[2],2)) * window["modelMaxDimension" + prNum], 4);
    	
    	var resultLine = document.createElement('h5');
    	resultLine.innerHTML = "Distance: " + distanceValue;
    	resultLine.setAttribute('id','measuringvalue'+prNum);
    	resultLine.setAttribute('data-measuring','true');
    	resultLine.setAttribute("style","text-align:center;");
    	$("#x3dElementTable" + prNum).after(resultLine);
    	
    	$("#measuringlinecoordinate"+prNum).attr("point", window["currentMeasureStart" + prNum][0] + " " + window["currentMeasureStart" + prNum][1] + " " + window["currentMeasureStart" + prNum][2]
		+ ", " + event.hitPnt[0] + " " + event.hitPnt[1] + " " + event.hitPnt[2]);   
    	
    	$("#x3dElement" + prNum).get(0).style.cursor = "auto";
    	
    	window["measuringMode" + prNum] = false;
    	
    	$("#x3dElementTable" + prNum).css("margin-bottom","+=20");
    	
    	event.cancelBubble = true;
    	event.stopPropagation();
    }
    
    function round_number(num, dec) {
        return Math.round(num * Math.pow(10, dec)) / Math.pow(10, dec);
    }
  //LIGHTING FUNCTIONS
    function changeLightingDirection(event, prNum){
    	if($("#x3dom_directional_light" + prNum).length == 0)
    		return;
    	
    	var directionArrayOld = $("#x3dom_directional_light" + prNum).attr("direction").split(" ");
		var directionArrayNew = new Array();
		directionArrayNew[0] = parseFloat(directionArrayOld[0]);
		directionArrayNew[1] = parseFloat(directionArrayOld[1]);
		directionArrayNew[2] = parseFloat(directionArrayOld[2]);
		var increment = 100;
		var maxHorizontal = 1 - Math.abs(parseFloat(directionArrayOld[1]));
		
		console.log("old: " + directionArrayOld[0] + " " + directionArrayOld[1] + " " + directionArrayOld[2]);
		if(event.clientX - window["mouseX" + prNum] > 0){
			if(parseFloat(directionArrayOld[0]) < 0){
				if(parseFloat(directionArrayOld[2]) + (event.clientX - window["mouseX" + prNum])/ increment * maxHorizontal > maxHorizontal){
					directionArrayNew[2] = maxHorizontal - (parseFloat(directionArrayOld[2]) + (event.clientX - window["mouseX" + prNum])/ increment * maxHorizontal  - maxHorizontal);
				}
				else{
					directionArrayNew[2] = parseFloat(directionArrayOld[2]) + (event.clientX - window["mouseX" + prNum])/ increment * maxHorizontal; 
				}
			}
			else if(parseFloat(directionArrayOld[0]) > 0){
				if(parseFloat(directionArrayOld[2]) - (event.clientX - window["mouseX" + prNum])/ increment * maxHorizontal < -maxHorizontal){
					directionArrayNew[2] = -maxHorizontal - (parseFloat(directionArrayOld[2]) - (event.clientX - window["mouseX" + prNum])/ increment * maxHorizontal + maxHorizontal);
				}
				else{
					directionArrayNew[2] = parseFloat(directionArrayOld[2]) - (event.clientX - window["mouseX" + prNum])/ increment * maxHorizontal; 
				}
			}
			else{
				if(parseFloat(directionArrayOld[2]) < 0){
					directionArrayNew[2] = parseFloat(directionArrayOld[2]) + (event.clientX - window["mouseX" + prNum])/ increment * maxHorizontal; 
				}
				else{
					directionArrayNew[2] = parseFloat(directionArrayOld[2]) - (event.clientX - window["mouseX" + prNum])/ increment * maxHorizontal;
				}
			}
			
			if(parseFloat(directionArrayOld[2]) > 0){
				if(parseFloat(directionArrayOld[0]) + (event.clientX - window["mouseX" + prNum])/ increment * maxHorizontal > maxHorizontal){
					directionArrayNew[0] = maxHorizontal - (parseFloat(directionArrayOld[0]) + (event.clientX - window["mouseX" + prNum])/ increment * maxHorizontal - maxHorizontal);
				}
				else{
					directionArrayNew[0] = parseFloat(directionArrayOld[0]) + (event.clientX - window["mouseX" + prNum])/ increment * maxHorizontal; 
				}
			}
			else if(parseFloat(directionArrayOld[2]) < 0){
				if(parseFloat(directionArrayOld[0]) - (event.clientX - window["mouseX" + prNum])/ increment * maxHorizontal < -maxHorizontal){
					directionArrayNew[0] = -maxHorizontal - (parseFloat(directionArrayOld[0]) - (event.clientX - window["mouseX" + prNum])/ increment * maxHorizontal + maxHorizontal);
				}
				else{
					directionArrayNew[0] = parseFloat(directionArrayOld[0]) - (event.clientX - window["mouseX" + prNum])/ increment * maxHorizontal; 
				}
			}
			else{
				if(parseFloat(directionArrayOld[0]) < 0){
					directionArrayNew[0] = parseFloat(directionArrayOld[0]) + (event.clientX - window["mouseX" + prNum])/ increment * maxHorizontal; 
				}
				else{
					directionArrayNew[0] = parseFloat(directionArrayOld[0]) - (event.clientX - window["mouseX" + prNum])/ increment * maxHorizontal;
				}
			}			
		}
		else if(event.clientX - window["mouseX" + prNum] < 0){  
			if(parseFloat(directionArrayOld[0]) < 0){
				if(parseFloat(directionArrayOld[2]) + (event.clientX - window["mouseX" + prNum])/ increment * maxHorizontal < -maxHorizontal){
					directionArrayNew[2] = -maxHorizontal - (parseFloat(directionArrayOld[2]) + (event.clientX - window["mouseX" + prNum])/ increment * maxHorizontal + maxHorizontal);
				}
				else{
					directionArrayNew[2] = parseFloat(directionArrayOld[2]) + (event.clientX - window["mouseX" + prNum])/ increment * maxHorizontal; 
				}
			}
			else if(parseFloat(directionArrayOld[0]) > 0){
				if(parseFloat(directionArrayOld[2]) - (event.clientX - window["mouseX" + prNum])/ increment * maxHorizontal > 1){
					directionArrayNew[2] = maxHorizontal - (parseFloat(directionArrayOld[2]) - (event.clientX - window["mouseX" + prNum])/ increment * maxHorizontal - maxHorizontal);
				}
				else{  
					directionArrayNew[2] = parseFloat(directionArrayOld[2]) - (event.clientX - window["mouseX" + prNum])/ increment * maxHorizontal; 
				}
			}
			else{
				if(parseFloat(directionArrayOld[2]) < 0){
					directionArrayNew[2] = parseFloat(directionArrayOld[2]) - (event.clientX - window["mouseX" + prNum])/ increment * maxHorizontal; 
				}
				else{
					directionArrayNew[2] = parseFloat(directionArrayOld[2]) + (event.clientX - window["mouseX" + prNum])/ increment * maxHorizontal;
				}
			}  
			
			if(parseFloat(directionArrayOld[2]) < 0){
				if(parseFloat(directionArrayOld[0]) - (event.clientX - window["mouseX" + prNum])/ increment * maxHorizontal > maxHorizontal){
					directionArrayNew[0] = maxHorizontal - (parseFloat(directionArrayOld[0]) + (event.clientX - window["mouseX" + prNum])/ increment * maxHorizontal - maxHorizontal);
				}
				else{
					directionArrayNew[0] = parseFloat(directionArrayOld[0]) - (event.clientX - window["mouseX" + prNum])/ increment * maxHorizontal; 
				}
			}
			else if(parseFloat(directionArrayOld[2]) > 0){
				if(parseFloat(directionArrayOld[0]) + (event.clientX - window["mouseX" + prNum])/ increment * maxHorizontal < -maxHorizontal){
					directionArrayNew[0] = -maxHorizontal - (parseFloat(directionArrayOld[0]) + (event.clientX - window["mouseX" + prNum])/ increment * maxHorizontal + maxHorizontal);
				}
				else{  
					directionArrayNew[0] = parseFloat(directionArrayOld[0]) + (event.clientX - window["mouseX" + prNum])/ increment * maxHorizontal;  
				}
			}
			else{
				if(parseFloat(directionArrayOld[0]) < 0){
					directionArrayNew[0] = parseFloat(directionArrayOld[0]) - (event.clientX - window["mouseX" + prNum])/ increment * maxHorizontal; 
				}
				else{
					directionArrayNew[0] = parseFloat(directionArrayOld[0]) + (event.clientX - window["mouseX" + prNum])/ increment * maxHorizontal;
				}
			}		
		}		
		console.log("new: " + directionArrayNew[0] + " " + directionArrayNew[1] + " " + directionArrayNew[2]);
		$("#x3dom_directional_light" + prNum).attr("direction", directionArrayNew[0] + " " + directionArrayNew[1] + " " + directionArrayNew[2]);
		
		///////////////////////////////////
		directionArrayOld = $("#x3dom_directional_light" + prNum).attr("direction").split(" ");
		directionArrayNew = new Array();
		directionArrayNew[0] = parseFloat(directionArrayOld[0]);
		directionArrayNew[1] = parseFloat(directionArrayOld[1]);
		directionArrayNew[2] = parseFloat(directionArrayOld[2]);
		var sideNotChanged = 1;
		
		if(event.clientY - window["mouseY" + prNum] > 0){
			if(parseFloat(directionArrayOld[2]) < 0){
				if(parseFloat(directionArrayOld[1]) - (event.clientY - window["mouseY" + prNum])/ increment < -1){
					directionArrayNew[1] = -1 - (parseFloat(directionArrayOld[1]) - (event.clientY - window["mouseY" + prNum])/ increment + 1);
					sideNotChanged = -1;
				}
				else{
					directionArrayNew[1] = parseFloat(directionArrayOld[1]) - (event.clientY - window["mouseY" + prNum])/ increment;					
				}				
			}
			else if(parseFloat(directionArrayOld[2]) > 0){
				if(parseFloat(directionArrayOld[1]) + (event.clientY - window["mouseY" + prNum])/ increment > 1){
					directionArrayNew[1] = 1 - (parseFloat(directionArrayOld[1]) + (event.clientY - window["mouseY" + prNum])/ increment - 1);
					sideNotChanged = -1;
				}
				else{
					directionArrayNew[1] = parseFloat(directionArrayOld[1]) + (event.clientY - window["mouseY" + prNum])/ increment;					
				}				
			}
			else{
				if(parseFloat(directionArrayOld[0]) < 0){
					if(parseFloat(directionArrayOld[1]) - (event.clientY - window["mouseY" + prNum])/ increment < -1){
						directionArrayNew[1] = -1 - (parseFloat(directionArrayOld[1]) - (event.clientY - window["mouseY" + prNum])/ increment + 1);
						sideNotChanged = -1;
					}
					else{
						directionArrayNew[1] = parseFloat(directionArrayOld[1]) - (event.clientY - window["mouseY" + prNum])/ increment;					
					}
				}
				else{
					if(parseFloat(directionArrayOld[1]) + (event.clientY - window["mouseY" + prNum])/ increment > 1){
						directionArrayNew[1] = 1 - (parseFloat(directionArrayOld[1]) + (event.clientY - window["mouseY" + prNum])/ increment - 1);
						sideNotChanged = -1;
					}
					else{
						directionArrayNew[1] = parseFloat(directionArrayOld[1]) + (event.clientY - window["mouseY" + prNum])/ increment;					
					}
				}
			}
		}  
		else if(event.clientY - window["mouseY" + prNum] < 0){
			if(parseFloat(directionArrayOld[2]) < 0){
				if(parseFloat(directionArrayOld[1]) - (event.clientY - window["mouseY" + prNum])/ increment > 1){
					directionArrayNew[1] = 1 - (parseFloat(directionArrayOld[1]) - (event.clientY - window["mouseY" + prNum])/ increment - 1);
					sideNotChanged = -1;
				}
				else{
					directionArrayNew[1] = parseFloat(directionArrayOld[1]) - (event.clientY - window["mouseY" + prNum])/ increment;					
				}				
			}
			else if(parseFloat(directionArrayOld[2]) > 0){
				if(parseFloat(directionArrayOld[1]) + (event.clientY - window["mouseY" + prNum])/ increment < -1){
					directionArrayNew[1] = -1 - (parseFloat(directionArrayOld[1]) + (event.clientY - window["mouseY" + prNum])/ increment + 1);
					sideNotChanged = -1;
				}
				else{
					directionArrayNew[1] = parseFloat(directionArrayOld[1]) + (event.clientY - window["mouseY" + prNum])/ increment;					
				}				
			}
			else{  
				if(parseFloat(directionArrayOld[0]) < 0){
					if(parseFloat(directionArrayOld[1]) - (event.clientY - window["mouseY" + prNum])/ increment > 1){
						directionArrayNew[1] = 1 - (parseFloat(directionArrayOld[1]) - (event.clientY - window["mouseY" + prNum])/ increment - 1);
						sideNotChanged = -1;
					}
					else{
						directionArrayNew[1] = parseFloat(directionArrayOld[1]) - (event.clientY - window["mouseY" + prNum])/ increment;					
					}
				}
				else{
					if(parseFloat(directionArrayOld[1]) + (event.clientY - window["mouseY" + prNum])/ increment < -1){
						directionArrayNew[1] = -1 - (parseFloat(directionArrayOld[1]) + (event.clientY - window["mouseY" + prNum])/ increment + 1);
						sideNotChanged = -1;
					}
					else{
						directionArrayNew[1] = parseFloat(directionArrayOld[1]) + (event.clientY - window["mouseY" + prNum])/ increment;					
					}
				}
			}
		}
		
		var maxHorizontalNew = 1 - Math.abs(parseFloat(directionArrayNew[1]));
		directionArrayNew[0] = directionArrayNew[0] / maxHorizontal * maxHorizontalNew * sideNotChanged;
		directionArrayNew[2] = directionArrayNew[2] / maxHorizontal * maxHorizontalNew * sideNotChanged;
		
		$("#x3dom_directional_light" + prNum).attr("direction", directionArrayNew[0] + " " + directionArrayNew[1] + " " + directionArrayNew[2]);
		
		if(window["reverseLighting" + prNum]){
			var directionArrayMain = $("#x3dom_directional_light" + prNum).attr("direction").split(" ");
			$("#x3dom_directional_light_reverse" + prNum).attr("direction", (-parseFloat(directionArrayMain[0])) + " " + (-parseFloat(directionArrayMain[1])) + " " + (-parseFloat(directionArrayMain[2])));
		}  
		
		event.cancelBubble = true;
		event.stopPropagation();
    }
    
    function toggleLight(e, prNum){
    	if(e.which == 106 || e.which == 74){
   		  if($("#x3dom_directional_light" + prNum).length == 1){
   			  window["savedLightDir" + prNum] = $("#x3dom_directional_light" + prNum).attr("direction");     			  
   			  $("#x3dom_directional_light" + prNum).remove();
   		  }
   		  else{ 
   			  var dirLight = document.createElement('directionalLight');
   			  dirLight.setAttribute("id", "x3dom_directional_light" + prNum);
   			  dirLight.setAttribute("direction", window["savedLightDir" + prNum]);
   			  dirLight.setAttribute("intensity", "1");
   			  dirLight.setAttribute("color", "1 1 1");
   			  dirLight.setAttribute("ambientIntensity", "0.0");
   			  dirLight.setAttribute("global", "true");
   			  $("#EyeCoords" + prNum).append(dirLight);  
   		  }
   	    }  	
    }
    
  /////////////////////  
    function handleObjectClick(event, prNum){
    	if(window["measuringMode" + prNum])
    		calculateDistance(event, prNum); 		
    	else if(window["isXClicked" + prNum])
    		 startMeasuring(event, prNum);
    	else{
	    	var annotationsTranslations = [];    	
	    	$("#x3dElement" + prNum + " > scene > transform[data-annotation]").each(function(){
	    		annotationsTranslations.push($(this).attr("translation").split(","));
	    	});
	    	for(var j = 0; j < annotationsTranslations.length; j++){  
	    		if(Math.sqrt(Math.pow(annotationsTranslations[j][0]-event.hitPnt[0],2)+Math.pow(annotationsTranslations[j][1]-event.hitPnt[1],2)+Math.pow(annotationsTranslations[j][2]-event.hitPnt[2],2)) <= 0.009){
	    			focusOnAnnotation(annotationsTranslations[j][0], annotationsTranslations[j][1], annotationsTranslations[j][2], event, prNum);
	    			return;
	    		}
	    	}
	    	
	    	if(window["isShiftClicked" + prNum])
	    		addAnnotation(event, prNum);
    	}
    }
    
    function handleMouseMove(event, prNum){
    	if(window["measuringMode" + prNum]){  
    		var theRuntime = $("#x3dElement" + prNum).get(0).runtime;
    		var mousePos = theRuntime.mousePosition(event);
    		var worldPos =  theRuntime.getViewingRay(mousePos[0], mousePos[1]).pos;

    		$("#measuringlinecoordinate"+prNum).attr("point", window["currentMeasureStart" + prNum][0] + " " + window["currentMeasureStart" + prNum][1] + " " + window["currentMeasureStart" + prNum][2]
    										+ ", " + worldPos.x + " " + worldPos.y + " " + worldPos.z);
    		
    		event.cancelBubble = true;
    		event.stopPropagation();
    	}
    	else if(window["isCClicked" + prNum] && ! window["mouseDown" + prNum])
    		changeLightingDirection(event, prNum);
    	
    	window["mouseX" + prNum] = event.clientX;
    	window["mouseY" + prNum] = event.clientY;    	
    }
    
    function handleViewpointChange(event){
    	var prNum = event.target.id.replace("x3dom_viewpoint_cam", "");
    	var rot = event.orientation;
    	
    	document.getElementById('EyeCoords'+ prNum).setAttribute(
    			'rotation', rot[0].x +' '+rot[0].y +' '+rot[0].z+' '+rot[1]);    	
    }
    
    function updatex3dPosition(prNum){
    	if(window["oldx3dposition" + prNum] != window["thisPreview" + prNum].offset().top){
    		$("#x3dElement" + prNum).attr("style", "position:absolute;top:" + (window["thisPreview" + prNum].offset().top + 288) + "px;");
    		window["oldx3dposition" + prNum] = window["thisPreview" + prNum].offset().top;
    	}
    }
    
(function ($, Configuration) {

  console.log("X3D previewer for " + Configuration.id);
  
  var fileUrl = "http://" + Configuration.hostIp + ":" + window.location.port + Configuration.url;
  var pathJs = "http://" + Configuration.hostIp + ":" + window.location.port + Configuration.jsPath + "/";
  
  var width = 750;
  var height = 550;
  
  var prNum = Configuration.tab.replace("#previewer","");
   
  //Annotations vars
  window["showAnnotations" + prNum] = false; 
  window["currentAnnotation" + prNum] = new Array(); 
  window["isCurrentSubmitted" + prNum] = true;
  window["annotationsCount" + prNum] = 0;
  window["isStatisticsExtended" + prNum] = false;
  window["isShiftClicked" + prNum] = false;
  window["configs" + prNum] = Configuration;
  window["width" + prNum] = width;
  
  //Measuring vars
  window["isXClicked" + prNum] = false;
  window["measuringMode" + prNum] = false;
  window["currentMeasureStart" + prNum] = new Array();
  window["modelMaxDimension" + prNum] = 1.0;
  
  //Lighting vars
  window["isCClicked" + prNum] = false;
//  window["currentLightingOffset" + prNum] = new Array(0,0,0);
//  window["currentViewpointRotation" + prNum] = new Array();
  window["mouseX" + prNum] = 0;
  window["mouseY" + prNum] = 0;
  window["mouseDown" + prNum] = false;
  window["reverseLighting" + prNum] = false;

  $(Configuration.tab).append("<table id='x3dElementTable" + prNum + "' style ='margin-bottom:560px;'><tr><td>Left mouse button drag</td><td>&nbsp;&nbsp;&nbsp;&nbsp;Rotate</td></tr>"
		  					+ "<tr><td>Left mouse button + Ctrl drag</td><td>&nbsp;&nbsp;&nbsp;&nbsp;Pan</td></tr>"
		  					+ "<tr><td>Right mouse button drag / Left mouse button + Alt drag</td><td>&nbsp;&nbsp;&nbsp;&nbsp;Zoom</td></tr>"
		  					+ "<tr><td>M</td><td>&nbsp;&nbsp;&nbsp;&nbsp;Change rendering (regular-vertices-wireframe)</td></tr>"
		  					+ "<tr><td>D</td><td>&nbsp;&nbsp;&nbsp;&nbsp;Show/hide texture maps</td></tr>"
		  					+ "<tr><td>Space</td><td>&nbsp;&nbsp;&nbsp;&nbsp;Show/hide model statistics</td></tr>"
		  					+ "<tr><td>Shift + Left mouse button</td><td>&nbsp;&nbsp;&nbsp;&nbsp;Add annotation</td></tr>"
		  					+ "<tr><td>Q</td><td>&nbsp;&nbsp;&nbsp;&nbsp;Toggle annotations visibility</td></tr>"
		  					+ "<tr><td>X + Left mouse button</td><td>&nbsp;&nbsp;&nbsp;&nbsp;Use measuring tool</td></tr>"
		  					+ "<tr><td>Z</td><td>&nbsp;&nbsp;&nbsp;&nbsp;Remove measurement</td></tr>"
		  					+ "<tr><td>J</td><td>&nbsp;&nbsp;&nbsp;&nbsp;Lighting on/off</td></tr>"
		  					+ "<tr><td>V</td><td>&nbsp;&nbsp;&nbsp;&nbsp;Single-sided/double-sided lighting</td></tr>"
		  					+ "<tr><td>C drag</td><td>&nbsp;&nbsp;&nbsp;&nbsp;Change lighting direction</td></tr>"
		  					+ "<tr><td></td><td>&nbsp;&nbsp;&nbsp;&nbsp;</td></tr>"
		  					+ "</table>");
  
  var inner = "<x3d id='x3dElement" + prNum + "' showStat='false' showLog='true' height='" + height + "px' width='" + width + "px' x='0px' y='0px' style='position:absolute;top:" + ($(Configuration.tab).offset().top + 288) + "px;' >";
  window["oldx3dposition" + prNum] = $(Configuration.tab).offset().top;
  window["thisPreview" + prNum] = $(Configuration.tab); 
  //setInterval(function(){updatex3dPosition(prNum);},50);
  setInterval("updatex3dPosition('" + prNum + "');", 50);

  $.ajax({
	    url: fileUrl,
	    async:false,
	    success: function (data) {
	    	inner = inner + data;
	    	 },
	    dataType: 'text'
	});
  inner = inner + "</x3d>";
  
  $(Configuration.tab).append(inner);
  
  $.ajax({
      url: Configuration.annotationsListPath,
      async:false,
      success: function (json) {   	  
    	  for(var i = 0; i < json.length; i++)
    		  createAnnotationPoint(json[i].x_coord,json[i].y_coord,json[i].z_coord,json[i].description, prNum);   	    	    		  
	  },
      dataType: "json"
    });
  

  $("#x3dElement" + prNum + " > scene > transform[data-actualshape] > shape > indexedfaceset").attr("DEF","model");
  $("#x3dElement" + prNum + " > scene > transform[data-actualshape] > shape > indexedfaceset").attr("solid","true");
  $("#x3dElement" + prNum + " > scene > transform[data-actualshape] > shape > indexedfaceset").attr("onclick","handleObjectClick(event,'" + prNum + "');");
  
  $("#x3dElement" + prNum + " > scene > transform").attr("render", "true");
  
  window["modelMaxDimension" + prNum] = $("#x3dElement" + prNum + " > scene").attr("data-modelMaxDimension");
  $("#x3dElement" + prNum).attr("onmousemove", "handleMouseMove(event,'" + prNum + "');");
    
  var s = document.createElement("script");
  s.type = "text/javascript";
  s.src = pathJs + "x3dom.js";
  console.log("Updating tab " + Configuration.tab);
  $(Configuration.tab).append(s); 
    
  var viewPoint = document.createElement('viewpoint');
  viewPoint.setAttribute("id", "x3dom_viewpoint_cam" + prNum);
  viewPoint.setAttribute("position", "0 0 2");
  viewPoint.setAttribute("centerOfRotation", "0,0,0");
  viewPoint.setAttribute("bind", "true");
  viewPoint.setAttribute("isActive", "true");
  $("#x3dElement" + prNum + " > scene").prepend(viewPoint);
  
  var navInfo = document.createElement('navigationInfo');
  navInfo.setAttribute("headlight", "false");
  navInfo.setAttribute("set_bind", "true");
  navInfo.setAttribute("isActive", "true");
  $("#x3dElement" + prNum + " > scene").prepend(navInfo);
  
  var directionalLight = document.createElement('directionalLight');
  directionalLight.setAttribute("id", "x3dom_directional_light" + prNum);
  directionalLight.setAttribute("direction", "0 0 -1");
  directionalLight.setAttribute("intensity", "1");
  directionalLight.setAttribute("color", "1 1 1");
  directionalLight.setAttribute("ambientIntensity", "0.0");
  directionalLight.setAttribute("global", "true");
  var lightTrafo = document.createElement('transform');
  lightTrafo.setAttribute("id", "EyeCoords" + prNum);
  lightTrafo.appendChild(directionalLight);  
  $("#x3dElement" + prNum + " > scene").prepend(lightTrafo);
  
  if(isPageLoaded){
	 x3dom.reload();
  }
  
  if(isPageLoaded == false){
		  document.onload = function() {
		      document.getElementById("x3dom_viewpoint_cam" + prNum).addEventListener('viewpointChanged', handleViewpointChange, false);		      
		      document.getElementById("x3dElement" + prNum).runtime.resetExamin();		      
		  };
	  }
  else{ 
	  document.getElementById("x3dom_viewpoint_cam" + prNum).addEventListener('viewpointChanged', handleViewpointChange, false);
      document.getElementById("x3dElement" + prNum).runtime.resetExamin();
  }
     
	  $("body").on('keypress','#x3dElement' + prNum,function(e){
		  if(e.which == 122 || e.which == 90){
			  window["measuringMode" + prNum] = false;
	
			  $("#measuringlinetrafo"+prNum).remove();
			  $("#measuringvalue"+prNum).each(function(){
				  $(this).remove();
				  $("#x3dElementTable" + prNum).css("margin-bottom","-=20");
			  });
			  
			  $("#x3dElement" + prNum).get(0).style.cursor = "auto";
		  }	  
		  else if(e.which == 113 || e.which == 81){
			  if(window["showAnnotations" + prNum]){
				  $("#x3dElement" + prNum + " > scene > transform[data-annotation]" 
				  + " > shape > appearance > material").attr("transparency", "1");
				  if(window["currentAnnotation" + prNum].length == 3){
					  if(window["isCurrentSubmitted" + prNum])
						  $("#x3dElement" + prNum + " > scene > transform[data-annotation][translation='" + window["currentAnnotation" + prNum][0] + "," + window["currentAnnotation" + prNum][1] + "," + window["currentAnnotation" + prNum][2] 
						  + "'] > shape > appearance > material").get(0).setAttribute("diffuseColor", "mediumblue");
					  else{
						  $("#x3dElement" + prNum + " > scene > transform[data-annotation][translation='" + window["currentAnnotation" + prNum][0] + "," + window["currentAnnotation" + prNum][1] + "," + window["currentAnnotation" + prNum][2] + "']").remove();
						  window["isCurrentSubmitted" + prNum] = true;
					  }   
					  clearConfigTabAnnotations(prNum);
					  window["currentAnnotation" + prNum] = new Array();
				  }
				  window["showAnnotations" + prNum] = false;
			  }else{
				  $("#x3dElement" + prNum + " > scene > transform[data-annotation]" 
				  + " > shape > appearance > material").attr("transparency", "0");
				  
				  window["showAnnotations" + prNum] = true; 
			  }		  
			  return false;
		  }
		  else if(e.which == 32){
			  if(window["isStatisticsExtended" + prNum]){
				  $("#x3dElementTable" + prNum).css("margin-bottom","-=160");
				  window["isStatisticsExtended" + prNum] = false;
			  }
			  else{
				  $("#x3dElementTable" + prNum).css("margin-bottom","+=160");
				  window["isStatisticsExtended" + prNum] = true;
			  }
			  
		  }
		  else if(e.which == 106 || e.which == 74){
	   		  if($("#x3dom_directional_light" + prNum).length == 1){
	   			  window["savedLightDir" + prNum] = $("#x3dom_directional_light" + prNum).attr("direction");     			  
	   			  $("#x3dom_directional_light" + prNum).remove();
	   			  $("#x3dom_directional_light_reverse" + prNum).remove();
	   		  }
	   		  else{ 
	   			  var dirLight = document.createElement('directionalLight');
	   			  dirLight.setAttribute("id", "x3dom_directional_light" + prNum);
	   			  dirLight.setAttribute("direction", window["savedLightDir" + prNum]);
	   			  dirLight.setAttribute("intensity", "1");
	   			  dirLight.setAttribute("color", "1 1 1");
	   			  dirLight.setAttribute("ambientIntensity", "0.0");
	   			  dirLight.setAttribute("global", "true");
	   			  $("#EyeCoords" + prNum).append(dirLight);
	   			  
	   			 if(window["reverseLighting" + prNum]){
	   				 var directionArrayMain = $("#x3dom_directional_light" + prNum).attr("direction").split(" ");
	
	   				 var dirLightReverse = document.createElement('directionalLight');
	   				 dirLightReverse.setAttribute("id", "x3dom_directional_light_reverse" + prNum);
	   				 dirLightReverse.setAttribute("direction", (-parseFloat(directionArrayMain[0])) + " " + (-parseFloat(directionArrayMain[1])) + " " + (-parseFloat(directionArrayMain[2])));
	   				 dirLightReverse.setAttribute("intensity", "1");
	   				 dirLightReverse.setAttribute("color", "1 1 1");
	   				 dirLightReverse.setAttribute("ambientIntensity", "0.0");
	   				 dirLightReverse.setAttribute("global", "true");
	   				 $("#EyeCoords" + prNum).append(dirLightReverse);
	   			 }  			  
	   		  }
	   	    }
		  else if(e.which == 118 || e.which == 86){
			  if(window["reverseLighting" + prNum]){
				  window["reverseLighting" + prNum] = false;
				  $("#x3dom_directional_light_reverse" + prNum).remove();
			  }
			  else{ 
				  window["reverseLighting" + prNum] = true;  
	
				  if($("#x3dom_directional_light" + prNum).length == 1){
					  var directionArrayMain = $("#x3dom_directional_light" + prNum).attr("direction").split(" ");
					  
					  var dirLightReverse = document.createElement('directionalLight');
					  dirLightReverse.setAttribute("id", "x3dom_directional_light_reverse" + prNum);
					  dirLightReverse.setAttribute("direction", (-parseFloat(directionArrayMain[0])) + " " + (-parseFloat(directionArrayMain[1])) + " " + (-parseFloat(directionArrayMain[2])));
					  dirLightReverse.setAttribute("intensity", "1");
					  dirLightReverse.setAttribute("color", "1 1 1");
					  dirLightReverse.setAttribute("ambientIntensity", "0.0");
					  dirLightReverse.setAttribute("global", "true");
					  $("#EyeCoords" + prNum).append(dirLightReverse); 
				  }
	
			  }
		  }
	  });
	  
	 
	  
	  $("body").on('keydown',function(e){
		  if(e.which == 16){
			  window["isShiftClicked" + prNum] = true;
		  }
		  else if(e.which == 88){
			  window["isXClicked" + prNum] = true;
		  }
		  else if(e.which == 67){
			  window["isCClicked" + prNum] = true;
		  }
	  });  
	  $("body").on('keyup',function(e){
		  if(e.which == 16){
			  window["isShiftClicked" + prNum] = false;
		  }
		  else if(e.which == 88){
			  window["isXClicked" + prNum] = false;
		  }
		  else if(e.which == 67){
			  window["isCClicked" + prNum] = false;
		  }
	  });
	  $("body").on('mouseup',function(){
		  window["mouseDown" + prNum] = false;
	  });
	  $("body").on('mousedown',function(){
		  window["mouseDown" + prNum] = true;
	  });
	  $("body").on('mousemove',function(e){
		  window["mouseX" + prNum] = e.clientX;
	  	  window["mouseY" + prNum] = e.clientY;
	  });

}(jQuery, Configuration));