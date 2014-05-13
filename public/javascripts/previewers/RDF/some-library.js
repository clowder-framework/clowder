function NavigateToSite(prNum){
    var ddl = document.getElementById("ddlMyList");
    var selectedVal = ddl.options[ddl.selectedIndex].value;

    window.open(selectedVal)
}

(function ($, Configuration) {
  console.log("RDF download interface for " + Configuration.id);
  
  console.log("Updating tab " + Configuration.tab);
  
  var prNum = Configuration.tab.replace("#previewer","");
  window["download"+prNum]
  
  $(Configuration.tab).append("<a href='" + "http://"+Configuration.hostIp+":"+window.location.port+ Configuration.url + "'>Download XML metadata as RDF</a>");

}(jQuery, Configuration));