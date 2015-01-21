#!/usr/bin/python -u
import sys
import json
import requests
import os
import time
import smtplib
import socket
import time
import urllib2
import getopt
import netifaces as ni

def main():
	"""Run extraction bus tests."""
	host = 'http://' + ni.ifaddresses('eth0')[2][0]['addr'] + ':9000/'
	key = 'r1ek3rs'
	suppress = False

	#Arguments
	opts, args = getopt.getopt(sys.argv[1:], 'h:s')

	for o, a in opts:
		if o == '-h':
			host = 'http://' + a + ':9000/'
		elif o == '-k':
			key = a
		elif o == '-s':
			suppress = True
		else:
			assert False, "unhandled option"

	print 'Testing: ' + host

	#Remove previous outputs
	for output_filename in os.listdir('tmp'):
		if(output_filename[0] != '.'):
			os.unlink('tmp/' + output_filename)

	#Read in tests
	with open('tests.txt', 'r') as tests_file:
		lines = tests_file.readlines()
		count = 0;
		mailserver = smtplib.SMTP('localhost')
		report = ''
		t0 = time.time()

		for line in lines:
			if not line.startswith('#'):
				parts = line.split(' ', 1)
				input_filename = parts[0]
				outputs = parts[1].split(',')

				for output in outputs:
					count += 1
					POSITIVE = True
					output = output.strip();

					#Check for negative tests
					if(output[0] == '!'):
						POSITIVE = False
						output = output[1:]

					#Check for input files
					if(output[0] == '"'):
						output = output[1:-1]
					else:
						if output.startswith("http://"):
							output = urllib2.urlopen(output).read(1000).strip()
						else:
							output = open(output).read(1000).strip()
					
					#Print out test
					if POSITIVE:	
						print(input_filename + ' -> "' + output + '"'),
					else:
						print(input_filename + ' -> !"' + output + '"'),

					#Run test
					metadata = extract(host, key, input_filename)
					#print metadata
				
					#Write derived data to a file for later reference
					output_filename = 'tmp/' + str(count) + '_' + os.path.splitext(os.path.basename(input_filename))[0] + '.txt'

					with open(output_filename, 'w') as output_file:
						output_file.write(metadata)
						
					os.chmod(output_filename, 0776)		#Give web application permission to overwrite

					#Check for expected output
					if not POSITIVE and metadata.find(output) is -1:
						print '\t\033[92m[OK]\033[0m'
					elif metadata.find(output) > -1:
						print '\t\033[92m[OK]\033[0m'
					else:
						print '\t\033[91m[Failed]\033[0m'

						#Send email notifying watchers	
						message = 'Test-' + str(count) + ' failed.  Expected output "'
								
						if not POSITIVE:
							message += '!'

						message += output + '" was not extracted from:\n\n' + input_filename + '\n\n'
						report += message;
						message += 'Report of last run can be seen here: \n\n http://' + socket.getfqdn() + '/dts/tests/tests.php?run=false&start=true\n'
						message = 'Subject: DTS Test Failed\n\n' + message;

						if not suppress:
							with open('watchers.txt', 'r') as watchers_file:
								watchers = watchers_file.readlines()
		
								for watcher in watchers:
									watcher = watcher.strip()
									mailserver.sendmail('', watcher, message)

		print 'Elapsed time: ' + timeToString(time.time() - t0)

		#Send a final report of failures
		if report:
			report = 'Subject: DTS Test Failure Report\n\n' + report
			report += 'Report of last run can be seen here: \n\n http://' + socket.getfqdn() + '/dts/tests/tests.php?run=false&start=true\n'

			with open('watchers.txt', 'r') as watchers_file:
				watchers = watchers_file.readlines()

				for watcher in watchers:
					watcher = watcher.strip()
					mailserver.sendmail('', watcher, report)

		mailserver.quit()

def extract(host, key, file):
	"""Pass file to Medici extraction bus."""
	#Upload file
	headers = {'Content-Type': 'application/json'}
	data = {}
	data["fileurl"] = file
	file_id = requests.post(host + 'api/extractions/upload_url?key=' + key, headers=headers, data=json.dumps(data)).json()['id']

	#Poll until output is ready (optional)
	while True:
		status = requests.get(host + 'api/extractions/' + file_id + '/status').json()
		if status['Status'] == 'Done': break
		time.sleep(1)

	#Display extracted content
	metadata = requests.get(host + 'api/extractions/' + file_id + '/metadata').json()
	return json.dumps(metadata)

def timeToString(t):
	"""Return a string represntation of the give elapsed time"""
	h = int(t / 3600);
	m = int((t % 3600) / 60);
	s = int((t % 3600) % 60);
			
	if h > 0:
		return str(round(h + m / 60.0, 2)) + ' hours';
	elif m > 0:
		return str(round(m + s / 60.0, 2)) + ' minutes';
	else:
		return str(s) + ' seconds';

if __name__ == '__main__':
	main()
