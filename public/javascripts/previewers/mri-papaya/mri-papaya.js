(function ($, Configuration) {

    var scriptElem = document.createElement("script");
    scriptElem.type = "text/javascript";
    scriptElem.src = Configuration.previewer + "/papaya.js";
    $(Configuration.tab).append(scriptElem);

    var styleSheetLink = document.createElement("link");
    styleSheetLink.rel = 'stylesheet';
    styleSheetLink.href = Configuration.previewer + "/papaya.css";
    $(Configuration.tab).append(styleSheetLink);

    var params = [];
    var papayaDivId = "papayaMain";
    params['images'] = [Configuration.url];

    //force the bottom control bar always on or the buttons will pop up in a weird place in the page
    params['showControlBar'] = true;

    //If you don't want the bottom controls, do this instead of showControlBar
    //params['showControls'] = false;

    var papayaDiv = document.createElement("div")
    papayaDiv.setAttribute("id", papayaDivId);
    $(Configuration.tab).append(papayaDiv);
    papaya.Container.addViewer(papayaDivId, params);

}(jQuery, Configuration));


