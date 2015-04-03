(function($, Configuration) {
	console.log("Dataset test previewer for " + Configuration.collection_id);

    console.log("Updating tab " + Configuration.div);
    
    var dataset_id = Configuration.dataset_id;

	// setting up ajax call to get metadata from the file
	var req = $.ajax({
		type: "GET",
		url: "/api/datasets/"+dataset_id+"/technicalmetadatajson",
		dataType: "json"
	});

	req.done(function(data){
		console.log("Metadata on dataset: ");
		console.log(data);
	});

	// setting up ajax call to get file from the dataset
	var file_req = $.ajax({
		type: "GET",
		url: "/api/datasets/"+dataset_id+"/listFiles",
		dataType: "json"
	});

	file_req.done(function(data){

		// create data object with counts compatible with d3
		var counts = {};
		for(i=0;i<data.length;i++) {
			if (data[i].contentType in counts) {
				var plusOne = counts[data[i].contentType] + 1;
				counts[data[i].contentType] = plusOne;
			} else {
				counts[data[i].contentType] = 1;
			}
		}
		var array = [];
		for(var x in counts){
			array.push({occurences: counts[x], mime: x});
		}

		$.getScript("http://d3js.org/d3.v3.min.js", function() {
			$.getScript(Configuration.path + "d3.legend.js", function() {

				$(Configuration.div).append('<h4>File Types</h4>');

				// add css
				$("<link/>", {
					rel: "stylesheet",
					type: "text/css",
					href: Configuration.path + "main.css"
				}).appendTo(Configuration.div);

				var width = 300,
					height = 250,
					radius = Math.min(width, height) / 2;

				var color = d3.scale.category20()
				//var color = d3.scale.ordinal()
				//	.range(["#98abc5", "#8a89a6", "#7b6888", "#6b486b", "#a05d56", "#d0743c", "#ff8c00"]);

				var arc = d3.svg.arc()
					.outerRadius(radius - 50)
					.innerRadius(radius - 80);

				var pie = d3.layout.pie()
					.sort(null)
					.value(function(d) { return d.occurences; });

				var svg = d3.select(Configuration.div).append("svg")
					.attr("width", width)
					.attr("height", height)
					.append("g")
					.attr("transform", "translate(" + (width / 2 - 50)+ "," + height / 2 + ")");

				var g = svg.selectAll(".arc")
					.data(pie(array))
					.enter().append("g")
					.attr("class", "arc");

				g.append("path")
					.attr("d", arc)
					.attr("data-legend", function(d){return d.data.mime})
					.style("fill", function(d) { return color(d.data.mime); });

				var legend = svg.append("g")
					.attr("class", "legend")
					.attr("transform", "translate("+ (radius - 35) +","+(-radius + 35)+")")
					.style("font-size", "12px")
					.call(d3.legend)

				// use if you want labels instead of legend
				//g.append("text")
				//	.attr("transform", function(d) {
				//		var desired = 140;
				//		var c = arc.centroid(d),
				//			x = c[0],
				//			y = c[1],
				//			h = Math.sqrt(x*x + y*y); // hypotenuse
				//		return "translate(" + (x/h * desired) +  ',' + (y/h * desired) +  ")";
				//	})
				//	.attr("dy", ".35em")
				//		.attr("text-anchor", function(d) { // which side
				//			return (d.endAngle + d.startAngle)/2 > Math.PI ?
				//				"end" : "start";
				//		})
				//	.attr("display", function(d) {
				//		return d.endAngle - d.startAngle > 0.01 ? "block" : "none";
				//	})
				//	.text(function(d) { return d.data.mime; });
			});
		});
	});
}(jQuery, Configuration));