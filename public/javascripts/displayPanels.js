// displayPanels.js
//
// Referenced by :
//    collectionList.scala.html
//    datasetList.scala.html
//    listspaces.scala.html



window.onload = function(){
  var pageSize = getParameterByName("size");
  if (pageSize){
    document.getElementById('numPageItems').value = pageSize;
  }
  var spaceType = sessionStorage.getItem("#spacesOwnership");
  if (spaceType)
    document.getElementById('spacesOwnership').value = spaceType;
};

function getValue(){
  var pageItems = $("#numPageItems").val();
  var url = window.location.href;
  window.location.href = updateQueryStringParameter(url, "size", pageItems);
};

function getSpaceType(owner){
  var type =$("#spacesOwnership").val();
  var url = window.location.href;
  if (type == 1) {
    window.location.href = updateQueryStringParameter(url, "showAll", false);
    sessionStorage.setItem("#spacesOwnership", "1");
  } else if (type == 2) {
    window.location.href = updateQueryStringParameter(url, "owner", owner);
    sessionStorage.setItem("#spacesOwnership", "2");
  } else {
    window.location.href = url.substring(0, url.indexOf("?"));
    sessionStorage.setItem("#spacesOwnership", "3");
  }
  updatePage();
};

function updateQueryStringParameter(uri, key, value) {
  var re = new RegExp("([?&])" + key + "=.*?(&|#|$)", "i");
  if( value === undefined ) {
    if (uri.match(re)) {
      return uri.replace(re, '$1$2');
    } else {
      return uri;
    }
  } else {
    if (uri.match(re)) {
      return uri.replace(re, '$1' + key + "=" + value + '$2');
    } else {
      var hash =  '';
      if( uri.indexOf('#') !== -1 ){
        hash = uri.replace(/.*#/, '#');
        uri = uri.replace(/#.*/, '');
      }
      var separator = uri.indexOf('?') !== -1 ? "&" : "?";
      return uri + separator + key + "=" + value + hash;
    }
  } 
};

function getParameterByName(name) {
  name = name.replace(/[\[]/, "\\[").replace(/[\]]/, "\\]");
  var regex = new RegExp("[\\?&]" + name + "=([^&#]*)"),
      results = regex.exec(location.search);
  return results === null ? "" : decodeURIComponent(results[1].replace(/\+/g, " "));
};


