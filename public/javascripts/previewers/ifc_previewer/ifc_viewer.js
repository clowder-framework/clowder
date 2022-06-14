/****************************************************************
 * @file ifc_viewer.js
 * Creating an IFC file viewer using the three.js library
 *
 * Language: JavaScript
 *
 * Chrysovalantis Constantinou
 *
 * The Cyprus Institute
 *
 * + 02/11/22 (cc): Created.
 * + 03/23/22 (cc): Functional version
 *
 ****************************************************************/

(function ($, Configuration) {
    let useTab = Configuration.tab;
    let referenceUrl = Configuration.url;
    let previewer = Configuration.previewer;

    console.log("IFC previewer");

    let script = document.createElement("script");
    script.type = "text/javascript";
    script.src = previewer + "/bundle.js";
    $(useTab).append(script);

}(jQuery, Configuration));
