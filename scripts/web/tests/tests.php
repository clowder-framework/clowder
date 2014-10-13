<!DOCTYPE html>
<html lang="en">
  <head>
    <meta charset="utf-8">
    <meta http-equiv="X-UA-Compatible" content="IE=edge">
    <meta name="viewport" content="width=device-width, initial-scale=1">
    <title>Brown Dog Resources Status</title>

    <!-- Bootstrap -->
    <link href="css/bootstrap.min.css" rel="stylesheet">

    <!-- HTML5 Shim and Respond.js IE8 support of HTML5 elements and media queries -->
    <!-- WARNING: Respond.js doesn't work if you view the page via file:// -->
    <!--[if lt IE 9]>
      <script src="https://oss.maxcdn.com/libs/html5shiv/3.7.0/html5shiv.js"></script>
      <script src="https://oss.maxcdn.com/libs/respond.js/1.4.2/respond.min.js"></script>
    <![endif]-->
  </head>

  <body>
		<br>
		<div class="container">
			<div class="jumbotron">
    		<h1>DTS Tests</h1>
				<input id="dts" type="text" class="form-control" value="<?php echo "http://" . $_SERVER['SERVER_NAME']; ?>">
				<div id="failures" style="color:#999999;font-style:italic;font-size:90%;"></div>
			</div>
				
			<input type="button" class="btn btn-lg btn-block btn-primary" value="Run Tests" onclick="start_tests()">
			<!--
			<div class="progress">
  			<div id="progress" class="progress-bar" role="progressbar" aria-valuenow="50" aria-valuemin="0" aria-valuemax="100" style="width: 50%">50%</div>
			</div>
			-->

			<table class="table table-bordered table-hover">
			<tr><th>#</th><th>Input</th><th>Output</th><th></th></tr>
		
			<?php
			$lines = file('tests.txt', FILE_IGNORE_NEW_LINES | FILE_SKIP_EMPTY_LINES);
			$json = array();
			$count = 0;		//Row ID and unique prefix for output file

			foreach($lines as $line) {
				if($line[0] != '#') {
					$parts = explode("\t", $line);
					$input_filename = $parts[0];
					$outputs = explode(',', $parts[1]);

					foreach($outputs as $output) {
						$count++;
						$json[$count-1]["file"] = $input_filename;
						$json[$count-1]["output"] = $output;
						
						$output_filename = basename($input_filename, pathinfo($input_filename, PATHINFO_EXTENSION)) . "txt";

						echo "<tr id=\"" . $count . "\">";
						echo "<td>" . $count . "</td>";
						echo "<td><a href=\"" . $input_filename . "\">" . $input_filename . "</a></td>";
						echo "<td><a href=\"tmp/" . $count . "_" . $output_filename . "\">" . $output . "</a></td>";
						echo "<td align=\"center\"><input type=\"button\" class=\"btn btn-xs btn-primary\" value=\"Run\" onclick=\"test(" . $count . ",'" . $input_filename . "','" . $output . "', false)\"></td>";
						echo "</tr>\n";
					}
				}
			}
			?>

			</table>
		</div>

    <!-- jQuery (necessary for Bootstrap's JavaScript plugins) -->
    <script src="https://ajax.googleapis.com/ajax/libs/jquery/1.11.1/jquery.min.js"></script>
    <!-- Include all compiled plugins (below), or include individual files as needed -->
    <script src="js/bootstrap.min.js"></script>

		<script>
			var tasks = $.parseJSON('<?php echo json_encode($json); ?>');
			var total = <?php echo $count; ?>;
			var task = 0;			//Row ID and unique prefix for output file
			var successes = 0;
			var run = true;
			var mail = false;

			<?php
			if(isset($_REQUEST['run'])) echo "run = " . $_REQUEST['run'] . ";\n";
			if(isset($_REQUEST['mail'])) echo "mail = " . $_REQUEST['mail'] . ";\n";
			if(isset($_REQUEST['start'])) echo "start_tests();\n";
			?>

			function start_tests() {
				task = 1;
				successes = 0;
				test(task, tasks[task-1]["file"], tasks[task-1]["output"], true);
			}

			function test(id, file, output, SPAWN_NEXT_TASK) {
				var row = document.getElementById(id.toString());
				$(row).addClass('info');
				$(row).attr('class', 'info');		//Set it again in case this is a second attempt

				var dts = document.getElementById('dts').value;
				var url = 'test.php?dts=' + encodeURIComponent(dts) + '&file=' + encodeURIComponent(file) + '&output=' + encodeURIComponent(output) + '&prefix=' + id + '&run=' + run + '&mail=' + mail;
	
				$.get(url, function(success) {
					//Check result
					if(success > 0) {
						$(row).attr('class', 'success');
						successes++;
					} else {
						$(row).attr('class', 'danger');
					}
				
					//Call next task
					if(SPAWN_NEXT_TASK) {
						//Update progress
						document.getElementById('failures').innerHTML = 'Failures: ' + (task - successes);

						if(task < total) {
							task++;
							test(task, tasks[task-1]["file"], tasks[task-1]["output"], true);
						}
					}
				});
			}
		</script>
  </body>
</html>
