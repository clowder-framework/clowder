(function(){
	var root = this;
	Preview = root.Preview = {};
	var Lifecycle = Preview.Lifecycle = {
			onStart: function(callback) {
				console.log("Started plugin");
				callback;
			}
	}
}());