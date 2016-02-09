var registerMDAutocomplete = function(inputElement, definitionData) {
  if (definitionData.type == 'list') {
    console.log('list found');
    $.ajax(definitionData.definitions_url, {
      method: "GET",
      dataType: 'jsonp'
    }).done(function(data) {
      $(inputElement).autocomplete({
        source: data
      });
    });
  } else if (definitionData.type == 'listjquery') {
    console.log('listjquery found');
    $(inputElement).autocomplete({
      source: function(request, response) {
        var d = {};
        d[definitionData.query_parameter] = request.term;
        $.ajax({
          url: definitionData.definitions_url,
          dataType: "jsonp",
          data: d,
          success: function(data) {
            console.log(data);
            var searchspace;
            if ('cat_data' in data) {
              searchspace = data.cat_data.concat(data.vars_data);
            } else {
              searchspace = data;
            }
            var searchwords = request.term.split(" ");
            $.each(searchwords, function() {
              searchspace = $.ui.autocomplete.filter(searchspace, this);
            });
            response(searchspace);
          }
        });
      }
    });
  } else {
    if ($(inputElement).prop('autocomplete')) {
      $(inputElement).autocomplete("destroy");
    }
  }
};

$(document).ready(function() {
// Assumption: the input list contains: <cat_short_name>;<cat_display_name> or <cat_short_name>:<var_name>, where <cat_display_name> contains no ":", and <var_name> contains no ";".
  $.widget( "custom.catcomplete", $.ui.autocomplete, {
    _create: function() {
      this._super();
      this.widget().menu( "option", "items", "> :not(.ui-autocomplete-category)" );
    },
    _renderMenu: function( ul, items ) {
      var that = this;
      var currentCategory = "";
      var category = "";
      var dispCatMap = new Map();
      $.each( items, function( index, item ) {
        //console.log("in renderMenu: item: ", item);
        var label = item.label;
        var char_index = label.indexOf(";");
        if (char_index > -1) {
          var shortName = label.slice(0, char_index);
          var displName = label.slice(char_index + 1);
          //console.log("Setting category name '" + shortName + "' to '" + displName + "'");
          dispCatMap.set(shortName, displName);
        }
      });
      $.each( items, function( index, item ) {
        var li;
        // Each item from an original array of strings was converted to an object { label: "val1", value: "val1" }.
        //console.log("item: ", item);
        var label = item.label;
        var char_index = label.indexOf(":");
        if (char_index > -1) {
          category = label.slice(0, char_index);
          item.label = label.slice(char_index + 1);
        }
        if ( category != currentCategory ) {
          //console.log("category changed from " + currentCategory + " to: " + category);
          var displayed_cat_name = category;
          if (dispCatMap.has(category)) {
            displayed_cat_name = dispCatMap.get(category);
          }
          ul.append( "<li class='ui-autocomplete-category'>" + displayed_cat_name + "</li>" );
          currentCategory = category;
        }
        // Used to be: Display only the items containing ":".
        // Don't display the items containing ";".
        if (label.indexOf(";") <= 0) {
          li = that._renderItemData( ul, item );
        }
      });
    }
  });
});
