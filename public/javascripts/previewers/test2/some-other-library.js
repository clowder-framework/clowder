(function ($, Configuration) {
  alert("Some other library is running on " + Configuration.bytes);
  Preview.Lifecycle.onStart();
//  var object = {};
//  _.extend(object, Preview.Lifecycle);
//  object.onStart(function(){alert("Extended");});
}(jQuery, Configuration));