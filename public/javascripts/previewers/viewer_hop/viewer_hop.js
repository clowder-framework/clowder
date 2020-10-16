/****************************************************************
  @file viewer_hop.js

  Loads the 3DHOP library and uses methods and functions to 
  load 3D models in point cloud (*.ply) and nexus (*.nxz) form
   into a clowder repository.   
 
  Language: JavaScript
  
  Chrysovalantis Constantinou
  The Cyprus Institute

  + 09/14/20 (cc): Created.
  + 10/14/20 (cc): Changing the source of the 3DHOP files
                   and load them online

****************************************************************/

(function ($, Configuration) {
  var useTab = Configuration.tab;
  var referenceUrl = Configuration.url;
  var confId = Configuration.id;
  var fileId = Configuration.fileid;
  var previewer = Configuration.previewer;

  var fileName = $('#file-name-title').text().trim();
  var fileNameExtension = fileName.substr(fileName.length - 3);
  var fileType;

  if (fileNameExtension == "ply") {
    fileType = "ply";
  }
  else if (fileNameExtension == "nxz") {
    fileType = "nexus";
  }

  //alert(fileName);
  //alert(typeof(fileName));
  //alert(fileNameExtension);
  //alert(fileType);

  // print all attributes of Configuration object

  /*for (const property in Configuration) 
  {
    alert(`${property}: ${Configuration[property]}`);
  }*/

  // add 3dhop.css to previewer

  $(useTab).append('<link rel="stylesheet" type="text/css" href="http://vcg.isti.cnr.it/3dhop/distribution/stylesheet/3dhop.css">');

  // load various 3dhop attributes (such as the background image) for 3dhop

  $(useTab).append($('<div/>', {
    id: '3dhop',
    class: 'tdhop',
    onmousedown: 'if (event.preventDefault) event.preventDefault()'
  }));

  $("#3dhop").append($('<div/>', {
    id: 'tdhlg'
  }));

  $("#3dhop").append($('<div/>', {
    id: 'toolbar'
  }));

  $("#toolbar").append("<img id='home' title='Home'  src='http://vcg.isti.cnr.it/3dhop/distribution/skins/dark/home.png'/><br/>");

  $("#toolbar").append("<img id='zoomin' title='Zoom In'  src='http://vcg.isti.cnr.it/3dhop/distribution/skins/dark/zoomin.png'/><br/>");
  $("#toolbar").append("<img id='zoomout' title='Zoom Out'  src='http://vcg.isti.cnr.it/3dhop/distribution/skins/dark/zoomout.png'/><br/>");

  $("#toolbar").append("<img id='light_on' title='Disable Light Control'  src='http://vcg.isti.cnr.it/3dhop/distribution/skins/dark/lightcontrol_on.png' style='position:absolute; visibility:hidden;'/>");
  $("#toolbar").append("<img id='light' title='Enable Light Control'  src='http://vcg.isti.cnr.it/3dhop/distribution/skins/dark/lightcontrol.png'/><br/>");

  $("#toolbar").append("<img id='measure_on' title='Disable Measure Tool'  src='http://vcg.isti.cnr.it/3dhop/distribution/skins/dark/measure_on.png' style='position:absolute; visibility:hidden;'/>");
  $("#toolbar").append("<img id='measure' title='Enable Measure Tool'  src='http://vcg.isti.cnr.it/3dhop/distribution/skins/dark/measure.png'/><br/>");

  $("#toolbar").append("<img id='pick_on' title='Disable PickPoint Mode'  src='http://vcg.isti.cnr.it/3dhop/distribution/skins/dark/pick_on.png' style='position:absolute; visibility:hidden;'/>");
  $("#toolbar").append("<img id='pick' title='Enable PickPoint Mode'  src='http://vcg.isti.cnr.it/3dhop/distribution/skins/dark/pick.png'/><br/>");

  $("#toolbar").append("<img id='full_on' title='Exit Full Screen'  src='http://vcg.isti.cnr.it/3dhop/distribution/skins/dark/full_on.png' style='position:absolute; visibility:hidden;'/>");
  $("#toolbar").append("<img id='full' title='Full Screen'  src='http://vcg.isti.cnr.it/3dhop/distribution/skins/dark/full.png'/>");

  $('#3dhop').append($('<div/>', {
    id: 'measure-box',
    class: 'output-box'
  }));

  $("#measure-box").text("Measured length");

  $("#measure-box").append($('<hr/>'));

  $("#measure-box").append($('<span/>', {
    id: 'measure-output',
    class: 'output-text',
    onmousedown: 'event.stopPropagation()'
  }));

  $("measure-output").text("0.0");

  $('#3dhop').append($('<div/>', {
    id: 'pickpoint-box',
    class: 'output-box'
  }));

  $("#pickpoint-box").text("XYZ picked point");

  $("#pickpoint-box").append($('<hr/>'));

  $("#pickpoint-box").append($('<span/>', {
    id: 'pickpoint-output',
    class: 'output-text',
    onmousedown: 'event.stopPropagation()'
  }));

  $("pickpoint-output").text("[ 0 , 0 , 0 ]");

  $("#3dhop").append($('<canvas/>', {
    id: 'draw-canvas',
    style: 'background-image: url("http://vcg.isti.cnr.it/3dhop/distribution/skins/backgrounds/dark.jpg")'
  }));

  // scripts  holds all the  3dhop files                                                                        

  var scripts = ["spidergl.js", "nexus.js", "ply.js", "trackball_sphere.js",
    "trackball_turntable.js", "trackball_pantilt.js", "trackball_turntable_pan.js", "init.js", "presenter.js"];

  // append the http address where the files are located

  for (index = 0; index < scripts.length; index++) {
    scripts[index] = "http://vcg.isti.cnr.it/3dhop/distribution/js/" + scripts[index];
  }

  // load 3dhop into the current tab   (old version)                                                                         

  /******for (index = 0; index < scripts.length; index++) {                                                            
       var s = document.createElement("script");                                                                   
       s.type = "text/javascript";                                                                                 
       //s.src = previewer + "/hop/js/" + scripts[index];                                                            
       s.src = scripts[index];
       $(useTab).append(s); 
     }             

  $(document).ready(function () {                                                                                                     
   init3dhop();                                                                                                                        
                                                                                                                                       
   setup3dhop(referenceUrl, fileType);                                                                                                 
                                                                                                                                       
   resizeCanvas(640, 480);                                                                                                             
                                                                                                                                       
   moveMeasurementbox(70, 243);                                                                                                        
   movePickpointbox(70, 301);                                                                                                          
                                                                                                                                       
   //moveToolbar(20, 20);                                                                                                              
 });*****/

  $.getScript(scripts[0], function () {
    $.getScript(scripts[1], function () {
      $.getScript(scripts[2], function () {
        $.getScript(scripts[3], function () {
          $.getScript(scripts[4], function () {
            $.getScript(scripts[5], function () {
              $.getScript(scripts[6], function () {
                $.getScript(scripts[7], function () {
                  $.getScript(scripts[8], function () {

                    init3dhop();

                    setup3dhop(referenceUrl, fileType);

                    resizeCanvas(640, 480);

                    moveMeasurementbox(70, 243);
                    movePickpointbox(70, 301);

                  })
                })
              })
            })
          })
        })
      })
    })
  });

}(jQuery, Configuration));

function getScripts(scripts, callback) {

  var progress = 0;

  scripts.forEach(function (script) {
    //alert(script);
    $.getScript(script, function () {
      if (++progress == scripts.length) callback();
    });
  });
}

var presenter = null;

function setup3dhop(address, fileType) {
  presenter = new Presenter("draw-canvas");

  presenter.setScene({
    meshes: {
      "mesh_1": {
        url: address,
        mType: fileType
      }
    },
    modelInstances: {
      "instance_1": { mesh: "mesh_1" }
    },
    trackball: {
      type: TurntablePanTrackball,
      trackOptions: {
        startDistance: 1.3,
        startPhi: 40.0,
        startTheta: 20.0,
        minMaxDist: [0.8, 2.5],
        minMaxPhi: [-180, 180],
        minMaxTheta: [-30.0, 70.0]
      }
    },
    space: {
      centerMode: "scene",
      radiusMode: "scene"
    }
  });

  presenter._onEndMeasurement = onEndMeasure;
  presenter._onEndPickingPoint = onEndPick;
}

function actionsToolbar(action) {
  if (action == 'home') presenter.resetTrackball();
  else if (action == 'zoomin') presenter.zoomIn();
  else if (action == 'zoomout') presenter.zoomOut();
  else if (action == 'light' || action == 'light_on') { presenter.enableLightTrackball(!presenter.isLightTrackballEnabled()); lightSwitch(); }
  else if (action == 'measure' || action == 'measure_on') { presenter.enableMeasurementTool(!presenter.isMeasurementToolEnabled()); measureSwitch(); }
  else if (action == 'full' || action == 'full_on') fullscreenSwitch();
  else if (action == 'pick' || action == 'pick_on') {
    presenter.enablePickpointMode(!presenter.isPickpointModeEnabled()); pickpointSwitch();
  }
}

function onEndMeasure(measure) {
  // measure.toFixed(2) sets the number of decimals when displaying the measure
  // depending on the model measure units, use "mm","m","km" or whatever you have
  $('#measure-output').html(measure.toFixed(2) + " mm");
}

function onEndPick(point) {
  var x = point[0].toFixed(2);
  var y = point[1].toFixed(2);
  var z = point[2].toFixed(2);
  $('#pickpoint-output').html("[ " + x + " , " + y + " , " + z + " ]");
}
