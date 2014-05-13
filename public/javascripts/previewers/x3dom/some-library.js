    function insertAfter(newElement,targetElement) {
    	var parent = targetElement.parentNode;     
    	if(parent.lastchild == targetElement) {
    		parent.appendChild(newElement);
    	} else {
    		parent.insertBefore(newElement, targetElement.nextSibling);
    	}
    }

//ANNOTATIONS FUNCTIONS
function clearConfigTabAnnotations(prNum){
  	  $("#annotFields"+prNum+" > textarea[data-annotation]").remove();
  	  $("#annotFields"+prNum+ " > button[data-annotation]").remove(); 
  	  $("#annotFields"+prNum+ " > h5[data-annotation]").remove();
  	  $("#annotFields"+prNum+ " > span[data-annotation]").remove();
  	  $("#annotFields"+prNum+ " > br[data-annotation]").remove();
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
        $("#annotFields"+prNum).append(annotationHeading);

        var annotationDescription = document.createElement('span');
        annotationDescription.innerHTML = $("#x3dElement" + prNum + " > scene > transform[data-annotation][translation='" + window["currentAnnotation" + prNum][0] + "," + window["currentAnnotation" + prNum][1] + "," + window["currentAnnotation" + prNum][2] + "'] > .annotationdescription").get(0).innerHTML;
        annotationDescription.setAttribute("style","display:block; width:" + window["width" + prNum] + "px;");
        annotationDescription.setAttribute('data-annotation','true');
        $("#annotFields"+prNum).append(annotationDescription);

        var annotationBreak = document.createElement('br');
        annotationBreak.setAttribute('data-annotation','true');
        $("#annotFields"+prNum).append(annotationBreak);

        var annotationEdit = document.createElement('button'); 	
        annotationEdit.setAttribute('type','button');
        annotationEdit.className = annotationEdit.className + " btn";
        annotationEdit.setAttribute("onclick","editAnnotationDescription('" + prNum + "')");
        annotationEdit.innerHTML = 'Edit';
        annotationEdit.setAttribute("style","margin-bottom: 5px;");
        annotationEdit.setAttribute('data-annotation','true');
        $("#annotFields"+prNum).append(annotationEdit);
        
        event.cancelBubble = true;
        event.stopPropagation();
      }
    }   
  	
    function createAnnotationPoint(x_coord, y_coord, z_coord, descriptionText, prNum){
  	  window["annotationsCount"+prNum]++; 

  	  var annotation = document.createElement('transform');
  	  annotation.setAttribute("id", "annotation_" + prNum+ "__" + window["annotationsCount"+prNum]);
  	  annotation.setAttribute("DEF", "annot_" + prNum+ "__" + window["annotationsCount"+prNum]);
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
  	  annotation['description'] = $("#annotFields"+prNum+ " > textarea[data-annotation]").get(0).value;
  	  
  	  var request = $.ajax({
  	       type: 'POST',
  	       async:false,
  	       url: window["annotationsEditPath" + prNum],
  	       data: JSON.stringify(annotation),
  	       contentType: "application/json",
  	     });
  	  
  		  request.done(function (response, textStatus, jqXHR){
  		        console.log("Response " + response);		        
  		        $("#x3dElement" + prNum + " > scene > transform[data-annotation][translation='" + window["currentAnnotation" + prNum][0] + "," + window["currentAnnotation" + prNum][1] + "," + window["currentAnnotation" + prNum][2] + "'] > .annotationdescription").get(0).innerHTML = $("#annotFields"+prNum+ " > textarea[data-annotation]").get(0).value;

  		        clearConfigTabAnnotations(prNum);
  		  	  	
  		        var newAnnotationHeading = document.createElement('h5');
  		        newAnnotationHeading.innerHTML = "Annotation description";
  		        newAnnotationHeading.setAttribute('data-annotation','true');
  		        newAnnotationHeading.setAttribute('style','font-weight: bold;');
  		        $("#annotFields"+prNum).append(newAnnotationHeading);

  		        var newAnnotationDescription = document.createElement('span');
  		        newAnnotationDescription.innerHTML = annotation['description'];
  		        newAnnotationDescription.setAttribute("style","display:block; width:" + window["width" + prNum] + "px;");
  		        newAnnotationDescription.setAttribute('data-annotation','true');
  		        $("#annotFields"+prNum).append(newAnnotationDescription);

  		        var annotationBreak = document.createElement('br');
  		        annotationBreak.setAttribute('data-annotation','true');
  		        $("#annotFields"+prNum).append(annotationBreak);

  		        var newAnnotationEdit = document.createElement('button'); 	
  		        newAnnotationEdit.setAttribute('type','button');
  		        newAnnotationEdit.className = newAnnotationEdit.className + " btn";
  		        newAnnotationEdit.setAttribute("onclick","editAnnotationDescription('" + prNum + "')");
  		        newAnnotationEdit.innerHTML = 'Edit';
  		        newAnnotationEdit.setAttribute("style","margin-bottom: 5px;");
  		        newAnnotationEdit.setAttribute('data-annotation','true');
  		        $("#annotFields"+prNum).append(newAnnotationEdit);	   
  		  	    		        
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
        annotationHeading.setAttribute('style','font-weight: bold;');
        $("#annotFields"+prNum).append(annotationHeading);  

        var annotationDescription = document.createElement('span');
        annotationDescription.innerHTML = $("#x3dElement" + prNum + " > scene > transform[data-annotation][translation='" + window["currentAnnotation" + prNum][0] + "," + window["currentAnnotation" + prNum][1] + "," + window["currentAnnotation" + prNum][2] + "'] > .annotationdescription").get(0).innerHTML;
        annotationDescription.setAttribute("style","display:block; width:" + window["width" + prNum] + "px;");
        annotationDescription.setAttribute('data-annotation','true');
        $("#annotFields"+prNum).append(annotationDescription);

        var annotationBreak = document.createElement('br');
        annotationBreak.setAttribute('data-annotation','true');
        $("#annotFields"+prNum).append(annotationBreak);

        var annotationEdit = document.createElement('button'); 	
        annotationEdit.setAttribute('type','button');
        annotationEdit.className = annotationEdit.className + " btn";
        annotationEdit.setAttribute("onclick","editAnnotationDescription('" + prNum + "')");
        annotationEdit.innerHTML = 'Edit';
        annotationEdit.setAttribute("style","margin-bottom: 5px;");
        annotationEdit.setAttribute('data-annotation','true');
        $("#annotFields"+prNum).append(annotationEdit);	
    }
    
    function editAnnotationDescription(prNum){
      clearConfigTabAnnotations(prNum);
  	  
  	  var editAnnotationHeading = document.createElement('h5');
  	  editAnnotationHeading.innerHTML = "Edit annotation description";
  	  editAnnotationHeading.setAttribute('data-annotation','true');
  	  editAnnotationHeading.setAttribute('style','font-weight: bold;');
  	  $("#annotFields"+prNum).append(editAnnotationHeading);
  	  
  	  var editAnnotationTextBox = document.createElement('textarea');
  	  editAnnotationTextBox.setAttribute("rows", "4");
  	  editAnnotationTextBox.setAttribute("style","width:" + (window["width" + prNum] - 15) + "px;");
  	  editAnnotationTextBox.value = $("#x3dElement" + prNum + " > scene > transform[data-annotation][translation='" + window["currentAnnotation" + prNum][0] + "," + window["currentAnnotation" + prNum][1] + "," + window["currentAnnotation" + prNum][2] + "'] > .annotationdescription").get(0).innerHTML;
  	  editAnnotationTextBox.setAttribute('data-annotation','true');
  	  $("#annotFields"+prNum).append(editAnnotationTextBox);
  	  
  	  var annotationBreak = document.createElement('br');
  	  annotationBreak.setAttribute('data-annotation','true');
  	  $("#annotFields"+prNum).append(annotationBreak);
  	  
  	  var editAnnotationSubmit = document.createElement('button'); 	
  	  editAnnotationSubmit.setAttribute('type','button');
  	  editAnnotationSubmit.className = editAnnotationSubmit.className + " btn";
  	  editAnnotationSubmit.setAttribute('style','margin-right:10px;');
  	  editAnnotationSubmit.setAttribute("onclick","submitAnnotationEdit('" + prNum + "')");
  	  editAnnotationSubmit.innerHTML = 'OK';
  	  editAnnotationSubmit.setAttribute("style","margin-bottom: 5px;");
  	  editAnnotationSubmit.setAttribute('data-annotation','true');
  	  $("#annotFields"+prNum).append(editAnnotationSubmit);
  	  
  	  var editAnnotationCancel = document.createElement('button'); 	
  	  editAnnotationCancel.setAttribute('type','button');
  	  editAnnotationCancel.className = editAnnotationCancel.className + " btn";
  	  editAnnotationCancel.setAttribute("onclick","cancelAnnotationEdit('" + prNum + "')");
  	  editAnnotationCancel.innerHTML = 'Cancel';
  	  editAnnotationCancel.setAttribute("style","margin-bottom: 5px;");
  	  editAnnotationCancel.setAttribute('data-annotation','true');
  	  $("#annotFields"+prNum).append(editAnnotationCancel);
  	  
    }
    
    function submitAnnotation(prNum){
  	  var annotation = {};
  	  annotation['x_coord'] = "" + window["currentAnnotation" + prNum][0];
  	  annotation['y_coord'] = "" + window["currentAnnotation" + prNum][1];
  	  annotation['z_coord'] = "" + window["currentAnnotation" + prNum][2];
  	  annotation['description'] = $("#annotFields"+prNum + " > textarea").get(0).value;
  	  var request = $.ajax({
  	       type: 'POST',
  	       async:false,
  	       url: window["annotationsAttachPath" + prNum],
  	       data: JSON.stringify(annotation),
  	       contentType: "application/json",
  	     });    

  		  request.done(function (response, textStatus, jqXHR){
  		        console.log("Response " + response);		        
  		        $("#x3dElement" + prNum + " > scene > transform[data-annotation][translation='" + window["currentAnnotation" + prNum][0] + "," + window["currentAnnotation" + prNum][1] + "," + window["currentAnnotation" + prNum][2] + "'] > .annotationdescription").get(0).innerHTML = $("#annotFields"+prNum + " > textarea[data-annotation]").get(0).value;
  		        window["isCurrentSubmitted" + prNum] = true;
  		        
  		        clearConfigTabAnnotations(prNum);
  		  	  	
  		  	    if(window["showAnnotations" + prNum]){
  		  		  var newAnnotationHeading = document.createElement('h5');
  		  		  newAnnotationHeading.innerHTML = "Annotation description";
  		  		  newAnnotationHeading.setAttribute('data-annotation','true');
  		  		  newAnnotationHeading.setAttribute('style','font-weight: bold;');
  		  		  $("#annotFields"+prNum).append(newAnnotationHeading);
  		  		  
  		  		  var newAnnotationDescription = document.createElement('span');
  		  		  newAnnotationDescription.innerHTML = annotation['description'];
  		  		  newAnnotationDescription.setAttribute("style","display:block; width:" + window["width" + prNum] + "px;");
  		  		  newAnnotationDescription.setAttribute('data-annotation','true');
  		  		  $("#annotFields"+prNum).append(newAnnotationDescription);
  		  		  
  		  		  var annotationBreak = document.createElement('br');
  		  		  annotationBreak.setAttribute('data-annotation','true');
  		  		  $("#annotFields"+prNum).append(annotationBreak);
  		  		  
  		  		  var newAnnotationEdit = document.createElement('button'); 	
  		  		  newAnnotationEdit.setAttribute('type','button');
  		  		  newAnnotationEdit.className = newAnnotationEdit.className + " btn";
  		  		  newAnnotationEdit.setAttribute("onclick","editAnnotationDescription('" + prNum + "')");
  		  		  newAnnotationEdit.innerHTML = 'Edit';
  		  		  newAnnotationEdit.setAttribute("style","margin-bottom: 5px;");
  		  		  newAnnotationEdit.setAttribute('data-annotation','true');
  		  		  $("#annotFields"+prNum).append(newAnnotationEdit);	
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
    	event.hitPnt[1] = event.hitPnt[1]-window["annotTrackingDiff2" + prNum];
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
    	newAnnotationHeading.setAttribute('style','font-weight: bold;');
    	$("#annotFields"+prNum).append(newAnnotationHeading);

    	var newAnnotationTextBox = document.createElement('textarea');
    	newAnnotationTextBox.setAttribute("rows", "4");
    	newAnnotationTextBox.setAttribute("style","width:" + (window["width" + prNum] - 15) + "px;");
    	newAnnotationTextBox.setAttribute('data-annotation','true');
    	$("#annotFields"+prNum).append(newAnnotationTextBox);

    	var annotationBreak = document.createElement('br');
    	annotationBreak.setAttribute('data-annotation','true');
    	$("#annotFields"+prNum).append(annotationBreak);

    	var newAnnotationSubmit = document.createElement('button'); 	
    	newAnnotationSubmit.setAttribute('type','button');
    	newAnnotationSubmit.className = newAnnotationSubmit.className + " btn";
    	newAnnotationSubmit.setAttribute('style','margin-right:10px;');
    	newAnnotationSubmit.setAttribute("onclick","submitAnnotation('" + prNum + "')");
    	newAnnotationSubmit.innerHTML = 'OK';
    	newAnnotationSubmit.setAttribute("style","margin-bottom: 5px;");
    	newAnnotationSubmit.setAttribute('data-annotation','true');
    	$("#annotFields"+prNum).append(newAnnotationSubmit);

    	var newAnnotationCancel = document.createElement('button'); 	
    	newAnnotationCancel.setAttribute('type','button');
    	newAnnotationCancel.className = newAnnotationCancel.className + " btn";
    	newAnnotationCancel.setAttribute("onclick","cancelNewAnnotation('" + prNum + "')");
    	newAnnotationCancel.innerHTML = 'Cancel annotation addition';
    	newAnnotationCancel.setAttribute("style","margin-bottom: 5px;");
    	newAnnotationCancel.setAttribute('data-annotation','true');
    	$("#annotFields"+prNum).append(newAnnotationCancel);

    	event.cancelBubble = true;
    	event.stopPropagation();
    }
    
  //MEASURING FUNCTIONS
    function startMeasuring(event, prNum){
		 window["measuringMode" + prNum] = true;
		 
		 $("#measuringlinetrafo"+ prNum).remove();
		 $("#measuringvalue"+ prNum).each(function(){
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
    	if(!(document.fullscreenElement || 
			       document.mozFullScreenElement || document.webkitFullscreenElement)){
    		resultLine.setAttribute("style","text-align:center;font-weight: bold;");
    		$("#x3dElementTable" + prNum).after(resultLine);
    	}
    	else{
    		resultLine.setAttribute("style","text-align: center; background-color: black; color: white; background: none repeat scroll 0% 0% transparent; max-height: 0%; position: absolute;margin-left: 80%; height:auto; bottom:150px;");
    		$("#x3dElement" + prNum + " > canvas").before(resultLine);
    	}
    		
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
    	else if(window["isXClicked" + prNum] && window["modelMaxDimension" + prNum] != "nocount")
    		 startMeasuring(event, prNum);
    	else{
	    	var annotationsTranslations = [];    	
	    	$("#x3dElement" + prNum + " > scene > transform[data-annotation]").each(function(){
	    		annotationsTranslations.push($(this).attr("translation").split(","));
	    	});	    	
	    	for(var j = 0; j < annotationsTranslations.length; j++){
	    		//alert(Math.sqrt(Math.pow(annotationsTranslations[j][0]-event.hitPnt[0],2)+Math.pow(annotationsTranslations[j][1]-event.hitPnt[1],2)+Math.pow(annotationsTranslations[j][2]-event.hitPnt[2],2)));
	    		//alert(annotationsTranslations[j] + " ||||| " +event.hitPnt);
	    		if(Math.sqrt(Math.pow(annotationsTranslations[j][0]-event.hitPnt[0],2)+Math.pow(annotationsTranslations[j][1]-event.hitPnt[1],2)+Math.pow(annotationsTranslations[j][2]-event.hitPnt[2],2)) <= 0.009 + window["annotTrackingDiff" + prNum]){
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
    		$("#x3dElement" + prNum).attr("style", "position:absolute;top:" + (window["thisPreview" + prNum].offset().top + window["x3dOffset" + prNum] - 80 - window["x3dOffset2" + prNum]) + "px;");
    		window["oldx3dposition" + prNum] = window["thisPreview" + prNum].offset().top;
    	}
    }
    
    function leaveFullscreen(prNum){
    	
    	$("#annotFields"+prNum).removeClass("fullscreenSubelement");
		
		var theDisplay = "";
		if(window["isStatisticsExtended" + prNum]){
			theDisplay = "inline";	
		  }
		  else{
			  theDisplay = "none";
		  }
		var statDiv = $("#x3dElement" + prNum + " > .x3dom-statdiv")[0];
		statDiv.setAttribute("style", "display: "+theDisplay+";");
		var thex3d = $("#x3dElement" + prNum)[0];
		var theCanvas = $("#x3dElement" + prNum + " > canvas")[0];
		var theAnnot = $("#annotFields"+prNum)[0];
		theAnnot.setAttribute("style","");
		thex3d.insertBefore(statDiv, theCanvas.nextSibling);
		insertAfter(theAnnot, thex3d);
		if($("#measuringvalue"+prNum).length > 0){
			var theMeasurement = $("#measuringvalue"+prNum)[0];
			theMeasurement.setAttribute("style","text-align: center;");						
			thex3d.parentNode.insertBefore(theMeasurement, thex3d);
		}
		
		$("#x3dElementTableFullscreen"+ prNum).css("display", "none");
		$("#x3dElement" + prNum + " > canvas").css("height", height+"px");
		$("#x3dElement" + prNum + " > canvas").css("width", width+"px");
		
		 if (document.cancelFullScreen) {
                document.cancelFullScreen();
            } else if (document.mozCancelFullScreen) {
                document.mozCancelFullScreen();
            } else if (navigator.userAgent.indexOf("hrome") != -1) {            	
            	if (document.webkitCancelFullScreen){            		
            			document.webkitCancelFullScreen();
            		}
                
                $("#x3dElement" + prNum).css("height", window["x3dNormalHeight" + prNum]);
				$("#x3dElement" + prNum).css("width", window["x3dNormalWidth" + prNum]);
				$("#x3dElement" + prNum).css("background-color", window["x3dNormalBgColor" + prNum]);
				$("#x3dElement" + prNum).css("top", window["x3dNormalTop" + prNum]);
							
				if(document.activeElement == $("#x3dElement" + prNum + " > canvas").get(0)){
					document.activeElement.blur();
				}
            }
		 window["annotTrackingDiff" + prNum] = 0.000;
		 window["annotTrackingDiff2" + prNum] = 0.000;	
    }
    
(function ($, Configuration) {

  console.log("X3D previewer for " + Configuration.id);
  
  var pathJs = "http://" + Configuration.hostIp + ":" + window.location.port + Configuration.jsPath + "/";
  
  width = 750;
  height = 550;
  
  var prNum = Configuration.tab.replace("#previewer","");
  
  if(isx3dActive && isPageLoaded){
	  $(Configuration.tab).append("<p><b>New 3D model added to dataset. Reload the webpage to see it.</b></p>");
	  return;
  }
   
  //Annotations vars
  window["showAnnotations" + prNum] = false; 
  window["currentAnnotation" + prNum] = new Array(); 
  window["isCurrentSubmitted" + prNum] = true;
  window["annotationsCount" + prNum] = 0;
  window["isStatisticsExtended" + prNum] = false;
  window["isShiftClicked" + prNum] = false;
  window["isCtrlClicked" + prNum] = false;
  window["configsTab" + prNum] = Configuration.tab;
  window["annotationsEditPath" + prNum] = Configuration.annotationsEditPath;
  window["annotationsAttachPath" + prNum] = Configuration.annotationsAttachPath;
  window["annotTrackingDiff" + prNum] = 0.000;
  window["annotTrackingDiff2" + prNum] = 0.000;
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
  
  window["exFullscreenElemExited"] = "#noElement";
  window["exFullscreenElemEntered"] = "#noElement";
  window["fullscreenExitedProperly"] = false;
  
  var x3dMeasureInstructions = "<tr><td>X + Left mouse button</td><td>&nbsp;&nbsp;&nbsp;&nbsp;Use measuring tool</td></tr>"
								+ "<tr><td>Z</td><td>&nbsp;&nbsp;&nbsp;&nbsp;Remove measurement</td></tr>";
  if(Configuration.wasPTM == 'false'){
	 if(navigator.userAgent.indexOf("hrome") == -1) 
	  window["x3dOffset" + prNum] = 308;
	 else
	  window["x3dOffset" + prNum] = 288;	 
  }	  
  else{
	if(navigator.userAgent.indexOf("hrome") == -1) 
	  window["x3dOffset" + prNum] = 268;
	else
      window["x3dOffset" + prNum] = 248;	
	  x3dMeasureInstructions = "";
  }  
  if(Configuration.calledFrom == "dataset")
	  window["x3dOffset2" + prNum] = 100;
  else if(Configuration.calledFrom == "file")
	  window["x3dOffset2" + prNum] = 200;
  

  $(Configuration.tab).append("<table id='x3dElementTable" + prNum + "' style ='margin-bottom:560px;'><tr><td>Left mouse button drag</td><td>&nbsp;&nbsp;&nbsp;&nbsp;Rotate</td></tr>" 
		  					+ "<tr><td>Ctrl + Left mouse button drag</td><td>&nbsp;&nbsp;&nbsp;&nbsp;Pan</td></tr>"
		  					+ "<tr><td>Right mouse button drag / Alt + Left mouse button drag</td><td>&nbsp;&nbsp;&nbsp;&nbsp;Zoom</td></tr>"
		  					+ "<tr><td>M</td><td>&nbsp;&nbsp;&nbsp;&nbsp;Change rendering (regular-vertices-wireframe)</td></tr>"
		  					+ "<tr><td>D</td><td>&nbsp;&nbsp;&nbsp;&nbsp;Show/hide texture maps</td></tr>"
		  					+ "<tr><td>Space</td><td>&nbsp;&nbsp;&nbsp;&nbsp;Show/hide model statistics</td></tr>"
		  					+ "<tr><td>Shift + Left mouse button</td><td>&nbsp;&nbsp;&nbsp;&nbsp;Add annotation</td></tr>"
		  					+ "<tr><td>Q</td><td>&nbsp;&nbsp;&nbsp;&nbsp;Toggle annotations visibility</td></tr>"
		  					+ x3dMeasureInstructions
		  					+ "<tr><td>J</td><td>&nbsp;&nbsp;&nbsp;&nbsp;Lighting on/off</td></tr>"
		  					+ "<tr><td>V</td><td>&nbsp;&nbsp;&nbsp;&nbsp;Single-sided/double-sided lighting</td></tr>"
		  					+ "<tr><td>C drag</td><td>&nbsp;&nbsp;&nbsp;&nbsp;Change lighting direction</td></tr>"
		  					+ "<tr><td>Shift + Q</td><td>&nbsp;&nbsp;&nbsp;&nbsp;Toggle fullscreen view</td></tr>"
		  					+ "<tr><td></td><td>&nbsp;&nbsp;&nbsp;&nbsp;</td></tr>"
		  					+ "</table>");
  
  var inner = "<x3d id='x3dElement" + prNum + "' showStat='false' showLog='true' height='" + height + "px' width='" + width + "px' x='0px' y='0px' style=' position:absolute;top:" + ($(Configuration.tab).offset().top + window["x3dOffset" + prNum] - window["x3dOffset2" + prNum]) + "px;' >"; 
  window["oldx3dposition" + prNum] = $(Configuration.tab).offset().top;
  window["thisPreview" + prNum] = $(Configuration.tab); 
  //setInterval(function(){updatex3dPosition(prNum);},50);
  setInterval("updatex3dPosition('" + prNum + "');", 50);

  $.ajax({
	    url: Configuration.url,
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
  if(Configuration.wasPTM == 'false')
	  window["modelMaxDimension" + prNum] = $("#x3dElement" + prNum + " > scene").attr("data-modelMaxDimension");
  else
	  window["modelMaxDimension" + prNum] = "nocount";
  $("#x3dElement" + prNum).attr("onmousemove", "handleMouseMove(event,'" + prNum + "');");
    
  if(!isx3dActive){
	  var s = document.createElement("script");
	  s.type = "text/javascript";
	  s.src = pathJs + "x3dom.js";
	  console.log("Updating tab " + Configuration.tab);
	  $(Configuration.tab).append(s);
  } 
    
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
	  if(!isx3dActive){		  
		  document.onload = function() {
			  $("x3d").each(function() {
				  document.getElementById("x3dom_viewpoint_cam" + $(this)[0].id.replace("x3dElement", "")).addEventListener('viewpointChanged', handleViewpointChange, false);
				  $(this)[0].runtime.resetExamin();
			  });			  
		  };
	  	}
	  }
  else{ 
	  document.getElementById("x3dom_viewpoint_cam" + prNum).addEventListener('viewpointChanged', handleViewpointChange, false);
      document.getElementById("x3dElement" + prNum).runtime.resetExamin();
  }
  isx3dActive = true;
  
  if(Configuration.wasPTM == 'true'){
	  $(Configuration.tab).append("<p>Model created using Bibliotheca Alexandrina's RTI-to-3D generator. The desktop GUI RTI-to-3D viewer can be downloaded from here:</br>" +
	  								"<a href='http://www.google.com' target='_blank' style='margin-bottom:10px;'>Temporary placeholder</a></p>");
  }
  
  if(x3dMeasureInstructions != "")
	  x3dMeasureInstructions = "<tr><td>X + Left mouse button&nbsp;&nbsp;&nbsp;&nbsp;</td><td>Use measuring tool</td></tr>"
		+ "<tr><td>Z&nbsp;&nbsp;&nbsp;&nbsp;</td><td>Remove measurement</td></tr>";
  $("#x3dElement" + prNum).append("<table id='x3dElementTableFullscreen" + prNum + "' class='fullscreenSubelement' style='display: none; color:white; background: none repeat scroll 0% 0% transparent; max-height: 0%; margin-top: 0%;position: relative;margin-left: 68.5%'><tr><td>Left mouse button drag&nbsp;&nbsp;&nbsp;&nbsp;</td><td>Rotate</td></tr>" 
			  					+ "<tr><td>Ctrl + Left mouse button drag&nbsp;&nbsp;&nbsp;&nbsp;</td><td>Pan</td></tr>"
			  					+ "<tr><td>Right mouse button drag / Alt + Left mouse button drag&nbsp;&nbsp;&nbsp;&nbsp;</td><td>Zoom</td></tr>"
			  					+ "<tr><td>M&nbsp;&nbsp;&nbsp;&nbsp;</td><td>Change rendering (regular-vertices-wireframe)</td></tr>"
			  					+ "<tr><td>D&nbsp;&nbsp;&nbsp;&nbsp;</td><td>Show/hide texture maps</td></tr>"
			  					+ "<tr><td>Space&nbsp;&nbsp;&nbsp;&nbsp;</td><td>Show/hide model statistics</td></tr>"
			  					+ "<tr><td>Shift + Left mouse button&nbsp;&nbsp;&nbsp;&nbsp;</td><td>Add annotation</td></tr>"
			  					+ "<tr><td>Q&nbsp;&nbsp;&nbsp;&nbsp;</td><td>Toggle annotations visibility</td></tr>"
			  					+ x3dMeasureInstructions
			  					+ "<tr><td>J&nbsp;&nbsp;&nbsp;&nbsp;</td><td>Lighting on/off</td></tr>"
			  					+ "<tr><td>V&nbsp;&nbsp;&nbsp;&nbsp;</td><td>Single-sided/double-sided lighting</td></tr>"
			  					+ "<tr><td>C drag&nbsp;&nbsp;&nbsp;&nbsp;</td><td>Change lighting direction</td></tr>"
			  					+ "<tr><td>Shift + Q&nbsp;&nbsp;&nbsp;&nbsp;</td><td>Toggle fullscreen view</td></tr>"
			  					+ "<tr><td>&nbsp;&nbsp;&nbsp;&nbsp;</td><td></td></tr>"
			  					+ "</table>");
  
  $(Configuration.tab).append("<div id='annotFields"+prNum+"'></div>");
  
  
	  $("body").on('keypress','#x3dElement' + prNum,function(e){
		  if(e.target.nodeName.toUpperCase() == "TEXTAREA")
			  return;
		  
		  if((e.which == 122 || e.which == 90) && window["modelMaxDimension" + prNum] != "nocount"){
			  window["measuringMode" + prNum] = false;
	
			  $("#measuringlinetrafo"+prNum).remove();
			  $("#measuringvalue"+prNum).each(function(){
				  $(this).remove();
				  $("#x3dElementTable" + prNum).css("margin-bottom","-=20");
			  });
			  
			  $("#x3dElement" + prNum).get(0).style.cursor = "auto";
			  
			  return false;
		  }	  
		  else if(e.which == 113 || e.which == 81){
			if(!window["isShiftClicked" + prNum])  
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
			else{
				if (!document.fullscreenElement &&    // alternative standard method
			       !document.mozFullScreenElement && !document.webkitFullscreenElement) {  // current working methods
					var x3d_element = $("#x3dElement" + prNum).get(0);
					
					if (x3d_element.requestFullscreen) {
		                x3d_element.requestFullscreen();
		            } else if (x3d_element.mozRequestFullScreen) {
		                x3d_element.mozRequestFullScreen();
		            } else if (x3d_element.webkitRequestFullscreen) {
		            	window["x3dNormalHeight" + prNum] = $("#x3dElement" + prNum).css("height");
		            	window["x3dNormalWidth" + prNum] = $("#x3dElement" + prNum).css("width");
		            	window["x3dNormalBgColor" + prNum] = $("#x3dElement" + prNum).css("background-color");
		            	window["x3dNormalTop" + prNum] = $("#x3dElement" + prNum).css("top");
		            	
		            	$("#x3dElement" + prNum).css("height", "100%");
						$("#x3dElement" + prNum).css("width", "100%");
						$("#x3dElement" + prNum).css("background-color", "black");
						$("#x3dElement" + prNum).css("top", "0px");
		                x3d_element.webkitRequestFullscreen(Element.ALLOW_KEYBOARD_INPUT);		                
		            }
					$("#x3dElement" + prNum + " > canvas").css("height", "100%");
					$("#x3dElement" + prNum + " > canvas").css("width", "100%");
					
					var statDiv = $("#x3dElement" + prNum + " > .x3dom-statdiv")[0];
					var thex3d = $("#x3dElement" + prNum)[0];
					var theCanvas = $("#x3dElement" + prNum + " > canvas")[0];
					var theAnnot = $("#annotFields"+prNum)[0];	
					
					if($("#x3dElementTableFullscreen"+ prNum+":last-child").size() == 1){
						thex3d.insertBefore($("#x3dElementTableFullscreen"+ prNum)[0], theCanvas);  
					}
					
					$("#x3dElementTableFullscreen"+ prNum).css("display", "block");
					
							
					theAnnot.setAttribute("style","background-color: black; color: white; background: none repeat scroll 0% 0% transparent; max-height: 0%; position: absolute;margin-left: 0.5%; height:auto; bottom:210px;");
					var theDisplay = "";
					if(window["isStatisticsExtended" + prNum]){
						theDisplay = "block";	
					  }
					  else{
						  theDisplay = "none";
					  }
					statDiv.setAttribute("style", "display: "+theDisplay+"; color: white; background: none repeat scroll 0% 0% transparent; max-height: 0%; margin-top: 15%;position: absolute;margin-left: 0.5%; ");
					
					thex3d.insertBefore(statDiv, theCanvas);
					thex3d.insertBefore(theAnnot, theCanvas);
					
					if($("#measuringvalue"+prNum).length > 0){
						var theMeasurement = $("#measuringvalue"+prNum)[0];
						theMeasurement.setAttribute("style","font-weight: bold; text-align: center; background-color: black; color: white; background: none repeat scroll 0% 0% transparent; max-height: 0%; position: absolute;margin-left: 80%; height:auto; bottom:150px;");						
						thex3d.insertBefore(theMeasurement, theCanvas);
					}
					
					window["annotTrackingDiff" + prNum] = 0.020;
					window["annotTrackingDiff2" + prNum] = 0.000;
					
					$("#annotFields"+prNum).addClass("fullscreenSubelement");
					
					window["exFullscreenElemEntered"] = "#x3dElement" + prNum;
				}
				else{
					window["fullscreenExitedProperly"] = true;					
					leaveFullscreen(prNum);
				}
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
			  return false; 
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
	   		 return false;
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
			  return false;
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
		  else if(e.which == 17){
			  window["isCtrlClicked" + prNum] = true;
		  }		  
		  else if((e.which == 27 || (e.which == 81 && window["isShiftClicked" + prNum])) && (document.fullscreenElement || 
			       document.mozFullScreenElement || document.webkitFullscreenElement)){
			  
			  if(document.activeElement.tagName.toLowerCase() !=  "canvas"){
				  console.log(document.activeElement.tagName);
				  var fullScreenElem;
				  if(document.mozFullScreenElement){
					  fullScreenElem = document.mozFullScreenElement;
				  }
				  else if(document.webkitFullscreenElement){
					  fullScreenElem = document.webkitFullscreenElement;
				  }
				  else{
					  fullScreenElem = document.fullscreenElement;
				  }
				  if(fullScreenElem.getAttribute('id').indexOf("x3dElement") != -1){
					 if(e.which != 27 && e.target !== undefined)
						 if(e.target.getAttribute('id') !== null)
							 if(navigator.userAgent.indexOf("hrome") == -1 || true){
								 e.preventDefault();
								  return false;
							 }
					 
					  var prNumber = fullScreenElem.getAttribute('id').replace("x3dElement","");
					  
					  window["fullscreenExitedProperly"] = true;
					  
					  
					  leaveFullscreen(prNumber);
					  
					  window["exFullscreenElemExited"] = "#x3dElement" + prNumber;
			  
					  
					  e.preventDefault();
					  return false;
				  }
		  }
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
		  else if(e.which == 17){
			  window["isCtrlClicked" + prNum] = false;
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
	  
	  
	  if(window["isx3domRefocusSet"] === undefined){
		  var fullscreenChangeHandler = function(){
			  if(!(document.fullscreenElement || 
				       document.mozFullScreenElement || document.webkitFullscreenElement) && window["exFullscreenElemEntered"] != "#noElement"){
				 				  
				  if(!window["fullscreenExitedProperly"]){
					  var prNumber = $(window["exFullscreenElemEntered"])[0].getAttribute('id').replace("x3dElement","");					  
					  leaveFullscreen(prNumber);
					  
				  }else{
					  window["fullscreenExitedProperly"] = false;
				  }
				  if($(window["exFullscreenElemExited"] + " > canvas").length > 0)
					  $(window["exFullscreenElemExited"] + " > canvas")[0].focus();
				  window["exFullscreenElemExited"] = "#noElement";
			  }			  
		  }; 
		  
		  document.addEventListener("fullscreenchange", fullscreenChangeHandler, false); 
		  document.addEventListener("mozfullscreenchange", fullscreenChangeHandler, false); 
		  document.addEventListener("webkitfullscreenchange", fullscreenChangeHandler, false); 
		  		  
		  window["isx3domRefocusSet"] = "set"; 
	  }
	  
	  

}(jQuery, Configuration));
