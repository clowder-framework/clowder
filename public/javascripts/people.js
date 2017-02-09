 function expandPeople() {
            $('.person').each(function() {
                var personElement = this;
                if(!$(personElement).hasClass('expanded')) {
                	$(personElement).addClass('expanded');
                var request = jsRoutes.api.Metadata.getPerson(this.textContent).ajax({
                    type: 'GET',
                    dataType: "json"
                });

                request.done(function (person) {
                	var name = person.familyName + ", " + person.givenName;
    				var html = "<a href='" + person['@id'] + "' target=_blank>" + name
    						+ "</a>";
    				personElement.innerHTML = html;
    				if(person.hasOwnProperty('email')) {
                      $(personElement).popover({
                        content:person.email,
                        placement:'top',
                        template: '<div class="popover" role="tooltip" style="max-width:600px;word-break:break-all"><div class="arrow"></div><h3 class="popover-title"></h3><div class="popover-content"></div></div>'
                      });
                      personElement.onmouseenter = function(){$(this).popover('show');};
                      personElement.onmouseleave = function(){$(this).popover('hide');};
    				}	
                });

                request.fail(function (jqXHR, textStatus, errorThrown){
                	if(jqXHR.status != 404) {
                        console.error("The following error occured: " + textStatus, errorThrown);
                  	}
                });
                }
            });
            
        }
 
 $( function() {
	 expandPeople();
 })