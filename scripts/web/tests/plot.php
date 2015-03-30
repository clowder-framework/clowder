<?php
$time_window = 7*24*60*60;      //Seconds in one week
$time_window *= 2;

//Connect to mongo
$m = new MongoClient();
$db = $m->{"tests"};
$collection = $db->{"dts"};
$cursor = $collection->find();

//Save the data
$fp = fopen("tmp/plot.txt", "w+");
$ff = fopen("tmp/plot_failures.txt", "w+");

foreach($cursor as $document) {
	//print_r($document);
	$time = $document["time"];
  $delta_time = time() - round($time/1000);

  if($delta_time < $time_window) {
		//$point = date('Y-m-d@H:i', $time/1000) . " " . $time . " " . $document["elapsed_time"];
		$point = $time . " " . $document["elapsed_time"];
		//echo "$point<br>\n";

		fwrite($fp, "$point\n");

		if(isset($document["failures"]) && $document["failures"]) {
			fwrite($ff, "$point\n");
		}
	}
}

fclose($fp);
fclose($ff);

//Call GNUPlot to generate a plot
exec("gnuplot plot.gnuplot");
?>

<img src="tmp/plot.png">
