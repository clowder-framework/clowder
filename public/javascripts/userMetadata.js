/*	var allowedNodes = [["Node 1", "Node"],["Node2", "Node"],["Node3", "Node"],["Node4", "Node"],
						["String1", "String"],["String2", "String"]];
							
	
	var allowedChildren = [["!root!","Node 1","1","1"],["Node 1","String1", "1", "*"],["Node 1","Node2", "0", "*"],["Node2","String1", "1", "1"],["Node2","Node3", "0", "1"],
						["Node3","String2", "0", "*"]];*/
					
	//CSV file format: Node and whether intermediate node or leaf.
	var context = {};
	window["allowedNodes"+topId] = new Array();	
	$.ajax({
	       url: window["modelIp"+topId] + '/user_metadata_model_allowedNodes.txt',
	       async:false,
		   success: function (data){
		   		var allowedNodesLines = data.split(/\r\n|\n/);
				for(var i = 0; i < allowedNodesLines.length; i++){
					window["allowedNodes"+topId][i] = allowedNodesLines[i].split(',');
				}
		   },
	       dataType: "text"
	     });
		 
	//CSV file format: Node,Child, Minimum child count, Maximum child count.	
	window["allowedChildren"+topId] = new Array();	
	$.ajax({
	       url: window["modelIp"+topId] + '/user_metadata_model_allowedRelationships.txt',
	       async:false,
		   success: function (data){
		   		var allowedChildrenLines = data.split(/\r\n|\n/);
				for(var i = 0; i < allowedChildrenLines.length; i++){
					window["allowedChildren"+topId][i] = allowedChildrenLines[i].split(',');
				}
		   },
	       dataType: "text"
	     });
	
				
	if(typeof usrmdFuncsAlreadyLoaded === "undefined")
	$(function() {
		
		$('body').on('click','.usr_md_,.usr_md_submit',function(e){
			var topId = $(this).closest("div").get(0).getAttribute('id');
						
			if($(this).is('button')){				
				   if($(this).html() == "Modify"){			  
					var textBox = document.createElement('input');
					textBox.classList.add('usr_md_');		   
					textBox.setAttribute('type', 'text');
					textBox.value = $(this).parent().children("span").get(0).innerHTML; 
					   
					$(this).parent().get(0).insertBefore(textBox, $(this).parent().children("span").get(0));					
					$(this).parent().children('span').remove();
					   
					$(this).html("Ok");							   		   			   
				  }
				  else if($(this).html() == "Ok"){
				  	var textSpan = document.createElement('span');
					textSpan.classList.add('usr_md_');
					textSpan.innerHTML = $(this).parent().children("input").get(0).value; 
						
					$(this).parent().get(0).insertBefore(textSpan, $(this).parent().children("input").get(0));
					$(this).parent().get(0).removeChild($(this).parent().children("input").get(0));
						 
					$(this).html("Modify");			
				  }			  
				  else if($(this).html() == "Delete"){
				  	$(this).parent().parent().get(0).removeChild($(this).parent().get(0));
				  }
				  else if($(this).html() == "Add property"){
					  
				  	var newProperty = document.createElement("li");
					newProperty.classList.add('usr_md_');
									
					var newPropertyMenu = document.createElement("select");
					newPropertyMenu.classList.add('usr_md_');
									
					var parentNodeType = "";
					if($(this).parent().is('div')){
						parentNodeType = "!root!";
					}
					else{
						parentNodeTypeText = $(this).parent().children('b').get(0).textContent;
						parentNodeType = parentNodeTypeText.substring(0, parentNodeTypeText.length - 1);
					}					
					var allowedChildrenForNode = window["allowedChildren"+topId].filter(function (a) {return a[0] == parentNodeType;});
					if(allowedChildrenForNode.length == 0){
                        notify('The metadata model states that this property cannot have subproperties of any kind.', 'error', true);
						return false;
					}
					$(this).parent().children('ul')[0].appendChild(newProperty);	
									
					for( var i = 0; i < allowedChildrenForNode.length; i++){
						var newOption = document.createElement("option");
						newOption.classList.add('usr_md_');					
						newOption.setAttribute('value', allowedChildrenForNode[i][1]);
						newOption.innerHTML = allowedChildrenForNode[i][1];
						if(i == 0){
							newOption.setAttribute('selected', 'selected');
						}
						
						newPropertyMenu.appendChild(newOption);					
					}
					newProperty.appendChild(newPropertyMenu);
					
					var newSelectButton = document.createElement('button'); 	
					newSelectButton.classList.add('usr_md_');
                    newSelectButton.classList.add('btn-link');
                    newSelectButton.classList.add('btn-sm');
					newSelectButton.setAttribute('type','button');		
					
					newSelectButton.innerHTML = 'Select property';
					newProperty.appendChild(newSelectButton);
						
				  }
				  else if($(this).html() == "Select property"){				  	
				  				
					var selectTag = $(this).parent().children('select')[0];
					var selectedProperty = selectTag.options[selectTag.selectedIndex].value;
					var selectedPropertyType = "Node";				
					for(var i = 0; ; i++){
						if(window["allowedNodes"+topId][i][0] == selectedProperty){
							selectedPropertyType = window["allowedNodes"+topId][i][1];
							break;
						}
					}  

					$(this).parent().children('select').remove();

					var newPropertyKey = document.createElement('b');

					newPropertyKey.classList.add('usr_md_');
					newPropertyKey.innerHTML = selectedProperty + ":";
					$(this).parent().get(0).insertBefore(newPropertyKey, $(this).get(0));						
					if(selectedPropertyType == "String"){										
						var textBox = document.createElement('input');
						textBox.classList.add('usr_md_');
									   
						textBox.setAttribute('type', 'text');
						textBox.textContent = "";
						$(this).parent().get(0).insertBefore(textBox, $(this).get(0));
						
						$(this).html("Ok");
						
						var newDeleteButton = document.createElement('button'); 	
						newDeleteButton.classList.add('usr_md_');
                        newDeleteButton.classList.add('btn-link');
                        newDeleteButton.classList.add('btn-sm');
						newDeleteButton.setAttribute('type','button');	
						
						newDeleteButton.innerHTML = 'Delete';
						$(this).parent().get(0).appendChild(newDeleteButton);
					}
					else{
						$(this).html("Add property");
						var newDeleteButton = document.createElement('button'); 	
						newDeleteButton.classList.add('usr_md_');
                        newDeleteButton.classList.add('btn-link');
                        newDeleteButton.classList.add('btn-sm');
						newDeleteButton.setAttribute('type','button');	
						
						newDeleteButton.innerHTML = 'Delete';
						$(this).parent().get(0).appendChild(newDeleteButton);
						
						var newPropertyList = document.createElement('ul');
						newPropertyList.classList.add('usr_md_');
						 
						$(this).parent().get(0).appendChild(newPropertyList);
					}				
				  }      
				  else if($(this).html() == "Submit"){
				  	var restrictionViolations = validateCardinalitiesToModel(document.getElementById(topId).children[1]);
					if(restrictionViolations != ''){
                        notify('<p>Institution metadata model violation(s): </p><p>' + restrictionViolations + '</p><p>Metadata not added.</p>','error', true);
						return false;
					}
					
					var data = DOMtoJSON(document.getElementById(topId).children[1]);
					data["@context"] = context;
                    context = {};
					var request = $.ajax({
				       type: 'POST',
				       url:  window["uploadIp"+topId],
				       data: JSON.stringify(data),
				       contentType: "application/json"
				     });
					 
					  request.done(function (response, textStatus, jqXHR){
						  notify('Metadata added successfully.', 'success', false, 5000);
					  });
					 
					  request.fail(function (jqXHR, textStatus, errorThrown){
						  console.error("The following error occured: "+ textStatus, errorThrown);
                          notify("Encountered error " + errorThrown + ". Metadata not added.", true)
					  });
					 
					 
				  }
				  else if($(this).html() == "Get as RDF"){
					  window.location = window["rdfIp"+topId];
				  }
				  
				  return false;
			}
		});
	 
	   
	   $('body').on('keypress','.usr_md_,.usr_md_submit',function(e){
	   	if($(this).is('input')){
		   	if(e.which == 13){
				$($(this).parent().children('button')[0]).click();			
				return false;
			}
		}
	   });
	   
	   function DOMtoJSON(branchRootNode){
	   			var branchData = {};				
				var childrenProperties = branchRootNode.children;
				for(var i = 0; i < childrenProperties.length; i++){
					if(childrenProperties[i].children[0].tagName.toLowerCase() == 'select')
						continue;	
					var key = childrenProperties[i].children[0].innerHTML;
					key = key.substring(0, key.length - 1);
                        // Code for finding the respective URI for the key
                        var url;
                    	for(var count=0;;count++)
                    	{
                    	    if(key == window["allowedNodes"+topId][count][0])
                    		    {
                    		        url = window["allowedNodes"+topId][count][2];
                    		        break;
                    		    }
                    	}
                        if(!(key in context))
                        {
                             // Adding the URI to the @context tag
                             context[key] = new Array(url);
                        }

					if(childrenProperties[i].children[1].tagName.toLowerCase() == 'span'){

						if(key in branchData){							
							branchData[key].push(childrenProperties[i].children[1].innerHTML);
						}
						else{	
							branchData[key] = new Array(childrenProperties[i].children[1].innerHTML);
						}  
					}else if(childrenProperties[i].children[1].tagName.toLowerCase() == 'button'){
						if(key in branchData){
							branchData[key].push(DOMtoJSON(childrenProperties[i].children[3]));
						}
						else{
							branchData[key] = new Array(DOMtoJSON(childrenProperties[i].children[3]));
						}				
					}

				}
				return branchData;
	   }
	   
	   function validateCardinalitiesToModel(branchRootNode){
	   		var restrictionViolations = "";
		
	   		var childrenProperties = branchRootNode.children;
			
			var branchRootNodeType = "";
			if(branchRootNode.parentElement.tagName.toLowerCase() == 'div'){
				branchRootNodeType = "!root!";
			}
			else{
				branchRootNodeTypeText = branchRootNode.parentElement.children[0].innerHTML;
				branchRootNodeType = branchRootNodeTypeText.substring(0, branchRootNodeTypeText.length - 1);
			}
			var allowedChildrenForNode = window["allowedChildren"+topId].filter(function (a) {return a[0] == branchRootNodeType;});
			
			for (var i = 0; i < allowedChildrenForNode.length; i++){
				var propertyInstancesCount = 0;
				for(var j = 0; j < childrenProperties.length; j++){
					if(childrenProperties[j].children[1].tagName.toLowerCase() == 'input' || childrenProperties[j].children[0].tagName.toLowerCase() == 'select')
						continue;
					var key = childrenProperties[j].children[0].innerHTML;
					key = key.substring(0, key.length - 1);					
					if(key == allowedChildrenForNode[i][1])
						propertyInstancesCount++;						
				}
				if(propertyInstancesCount < allowedChildrenForNode[i][2]){
					if(branchRootNodeType == "!root!")
						restrictionViolations+= "Must have at least " + allowedChildrenForNode[i][2] + " " + allowedChildrenForNode[i][1] + "(s).";
					else
						restrictionViolations+= "Must have at least " + allowedChildrenForNode[i][2] + " " + allowedChildrenForNode[i][1] + "(s) for each " + allowedChildrenForNode[i][0] + ".";	 
				}
				if(allowedChildrenForNode[i][3] != '*' && propertyInstancesCount > allowedChildrenForNode[i][3]){
					if(branchRootNodeType == "!root!")
						restrictionViolations+= "Must have no more than " + allowedChildrenForNode[i][3] + " " + allowedChildrenForNode[i][1] + "(s).";
					else
						restrictionViolations+= "Must have no more than " + allowedChildrenForNode[i][3] + " " + allowedChildrenForNode[i][1] + "(s) for each " + allowedChildrenForNode[i][0] + ".";	 
				}				
			}
			
			if(restrictionViolations != '')
				return restrictionViolations;
			
			for (i = 0; i < childrenProperties.length; i++){
				if(childrenProperties[i].children[1].tagName.toLowerCase() != 'span'){
					if(childrenProperties[i].children[1].tagName.toLowerCase() == 'input' || childrenProperties[i].children[0].tagName.toLowerCase() == 'select')
						continue;
					restrictionViolations+= validateCardinalitiesToModel(childrenProperties[i].children[3]);
					
					if(restrictionViolations != '')
						return restrictionViolations;
				}
			}
			
			return '';
	   }
	   	   	  
	 });
	
	usrmdFuncsAlreadyLoaded = true;