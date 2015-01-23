<?php
//Connect to mongo
$m = new MongoClient();
$db = $m->{"tests"};
$collection = $db->{"dts"};
$cursor = $collection->find();

//Save the data
$fp = fopen("tmp/plot.txt", "w+");

foreach($cursor as $document) {
	//print_r($document);
	//$point = date('Y-m-d@H:i', $document["time"]/1000) . " " . $document["time"] . " " . $document["elapsed_time"];
	$point = $document["time"] . " " . $document["elapsed_time"];
	fwrite($fp, "$point\n");
	//echo "$point<br>\n";
}

fclose($fp);

//Call GNUPlot to generate a plot
exec("gnuplot plot.gnuplot");
?>

<img src="tmp/plot.png">
