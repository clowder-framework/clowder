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
import thread
import threading
from pymongo import MongoClient

failure_report = ''
enable_threads = False
threads = 0
lock = threading.Lock()

def main():
	"""Run extraction bus tests."""
	global failure_report
	global enable_threads
	global threads
	global lock
	host = ni.ifaddresses('eth0')[2][0]['addr']
	hostname = ''
	port = '9000'
	key = 'r1ek3rs'
	all_failures = False

	#Arguments
	opts, args = getopt.getopt(sys.argv[1:], 'h:p:k:n:at')

	for o, a in opts:
		if o == '-h':
			host = a
		elif o == '-n':
			hostname = a
		elif o == '-p':
			port = a
		elif o == '-k':
			key = a
		elif o == '-a':
			all_failures = True
		elif o == '-t':
			enable_threads = True
		else:
			assert False, "unhandled option"

	print 'Testing: ' + host + '\n'

	#Remove previous outputs
	for output_filename in os.listdir('tmp'):
		if(output_filename[0] != '.' and output_filename != 'failures.txt'):
			os.unlink('tmp/' + output_filename)

	#Read in tests
	with open('tests.txt', 'r') as tests_file:
		lines = tests_file.readlines()
		count = 0;
		t0 = time.time()
		comment = ''
		runs = 1

		for line in lines:
			line = line.strip()

			if line and line.startswith('@'):
				comment= line[1:]

				if not enable_threads:
					print comment + ': '
			elif line and line.startswith('*'):
				runs = int(line[1:])
			elif line and not line.startswith('#'):
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
		
					#Run the test	
					if enable_threads:
						for i in range(0, runs):
							with lock:
								threads += 1

							thread.start_new_thread(run_test, (host, hostname, port, key, input_filename, output, POSITIVE, count, comment, all_failures))
					else:
						for i in range(0, runs):
							run_test(host, hostname, port, key, input_filename, output, POSITIVE, count, comment, all_failures)

					#Set runs back to one for next test
					comment = ''
					runs = 1

		#Wait for threads if any
		if enable_threads:
			while threads:
				time.sleep(1)

		dt = time.time() - t0
		print 'Elapsed time: ' + timeToString(dt)

    #Save to mongo
		client = MongoClient()
		db = client['tests']
		collection = db['dts']

		if failure_report:
			document = {'time': int(round(time.time()*1000)), 'elapsed_time': dt, 'failures': True}
		else:
			document = {'time': int(round(time.time()*1000)), 'elapsed_time': dt, 'failures': False}

		collection.insert(document)

		#Send a final report of failures
		if failure_report:
			#Save current failure report to a file
			with open('tmp/failures.txt', 'w') as output_file:
				output_file.write(failure_report)
		
			#Send failure notification emails
			with open('failure_watchers.txt', 'r') as watchers_file:
				watchers = watchers_file.read().splitlines()
	
				if hostname:
					message = 'From: \"' + hostname + '\" <devnull@ncsa.illinois.edu>\n'
				else:
					message = 'From: \"' + host + '\" <devnull@ncsa.illinois.edu>\n'

				message += 'To: ' + ', '.join(watchers) + '\n'
				message += 'Subject: DTS Test Failure Report\n\n'
				message += failure_report			
				message += 'Report of last run can be seen here: \n\n http://' + socket.getfqdn() + '/dts/tests/tests.php?dts=' + host + '&run=false&start=true\n\n'
				message += 'Elapsed time: ' + timeToString(dt)

				mailserver = smtplib.SMTP('localhost')
				for watcher in watchers:
					mailserver.sendmail('', watcher, message)
				mailserver.quit()
		else:
			if os.path.isfile('tmp/failures.txt'):
				#Send failure rectification emails
				with open('tmp/failures.txt', 'r') as report_file:
					failure_report = report_file.read()
					os.unlink('tmp/failures.txt')
		
					with open('failure_watchers.txt', 'r') as watchers_file:
						watchers = watchers_file.read().splitlines()
	
						if hostname:
							message = 'From: \"' + hostname + '\" <devnull@ncsa.illinois.edu>\n'
						else:
							message = 'From: \"' + host + '\" <devnull@ncsa.illinois.edu>\n'

						message += 'To: ' + ', '.join(watchers) + '\n'
						message += 'Subject: DTS Tests Now Passing\n\n'
						message += 'Previous failures:\n\n'
						message += failure_report			
						message += 'Report of last run can be seen here: \n\n http://' + socket.getfqdn() + '/dts/tests/tests.php?dts=' + host + '&run=false&start=true\n\n'
						message += 'Elapsed time: ' + timeToString(dt)

						mailserver = smtplib.SMTP('localhost')
						for watcher in watchers:
							mailserver.sendmail('', watcher, message)
						mailserver.quit()
			else:
				#Send success notification emails
				with open('pass_watchers.txt', 'r') as watchers_file:
					watchers = watchers_file.read().splitlines()

					if hostname:
						message = 'From: \"' + hostname + '\" <devnull@ncsa.illinois.edu>\n'
					else:
						message = 'From: \"' + host + '\" <devnull@ncsa.illinois.edu>\n'

					message += 'To: ' + ', '.join(watchers) + '\n'
					message += 'Subject: DTS Tests Passed\n\n';
					message += 'Elapsed time: ' + timeToString(dt)

					mailserver = smtplib.SMTP('localhost')
					for watcher in watchers:
						mailserver.sendmail('', watcher, message)
					mailserver.quit()

def run_test(host, hostname, port, key, input_filename, output, POSITIVE, count, comment, all_failures):
	"""Run a test."""
	global failure_report
	global enable_threads
	global threads
	global lock

	#Print out test
	if enable_threads:
		with lock:
			if POSITIVE:	
				print	input_filename + ' -> "' + output + '"\t\033[94m[Running]\033[0m'
			else:
				print	input_filename + ' -> !"' + output + '"\t\033[94m[Running]\033[0m'
	else:
		if POSITIVE:	
			print(input_filename + ' -> "' + output + '"\t'),
		else:
			print(input_filename + ' -> !"' + output + '"\t'),

	#Run test
	metadata = extract(host, port, key, input_filename, 60)
	#print '\n' + metadata
				
	#Write derived data to a file for later reference
	output_filename = 'tmp/' + str(count) + '_' + os.path.splitext(os.path.basename(input_filename))[0] + '.txt'

	with open(output_filename, 'w') as output_file:
		output_file.write(metadata)
						
	os.chmod(output_filename, 0776)		#Give web application permission to overwrite
		
	#Display result
	if enable_threads:
		with lock:
			if POSITIVE:	
				print(input_filename + ' -> "' + output + '"\t'),
			else:
				print(input_filename + ' -> !"' + output + '"\t'),
	
			if not POSITIVE and metadata.find(output) is -1:
				print '\033[92m[OK]\033[0m'
			elif metadata.find(output) > -1:
				print '\033[92m[OK]\033[0m'
			else:
				print '\033[91m[Failed]\033[0m'

	#Check for expected output and add to report
	if not POSITIVE and metadata.find(output) is -1:
		if not enable_threads:
			print '\033[92m[OK]\033[0m\n'
	elif metadata.find(output) > -1:
		if not enable_threads:
			print '\033[92m[OK]\033[0m\n'
	else:
		if not enable_threads:
			print '\033[91m[Failed]\033[0m\n'

		report = ''

		if comment:
			report = 'Test-' + str(count) + ' failed: ' + comment + '.  Expected output "'
		else:
			report = 'Test-' + str(count) + ' failed.  Expected output "'
								
		if not POSITIVE:
			report += '!'

		report += output + '" was not extracted from:\n\n' + input_filename + '\n\n'

		if enable_threads:
			with lock:
				failure_report += report;
		else:
			failure_report += report;

	#Send email notifying watchers	
	if all_failures:
		with open('failure_watchers.txt', 'r') as watchers_file:
			watchers = watchers_file.read().splitlines()
							
			if hostname:	
				message = 'From: \"' + hostname + '\" <devnull@ncsa.illinois.edu>\n'
			else:
				message = 'From: \"' + host + '\" <devnull@ncsa.illinois.edu>\n'

			message += 'To: ' + ', '.join(watchers) + '\n'
			message += 'Subject: DTS Test Failed\n\n'
			message += report
			message += 'Report of last run can be seen here: \n\n http://' + socket.getfqdn() + '/dts/tests/tests.php?dts=' + host + '&run=false&start=true\n'

			mailserver = smtplib.SMTP('localhost')
			for watcher in watchers:
				mailserver.sendmail('', watcher, message)
			mailserver.quit()

	#If in a thread decrement the thread counter
	with lock:
		if threads:
			threads -= 1

def extract(host, port, key, file, wait):
	"""Pass file to Medici extraction bus."""
	#Upload file
	headers = {'Content-Type': 'application/json'}
	data = {}
	data["fileurl"] = file
	file_id = requests.post('http://' + host + ':' + port + '/api/extractions/upload_url?key=' + key, headers=headers, data=json.dumps(data)).json()['id']

	#Poll until output is ready (optional)
	while wait > 0:
		status = requests.get('http://' + host + ':' + port + '/api/extractions/' + file_id + '/status').json()
		if status['Status'] == 'Done': break
		time.sleep(1)
		wait -= 1

	#Display extracted content (TODO: needs to be one endpoint!!!)
	metadata = requests.get('http://' + host + ':' + port + '/api/extractions/' + file_id + '/metadata').json()
	metadata["technicalmetadata"] = requests.get('http://' + host + ':' + port + '/api/files/' + file_id + '/technicalmetadatajson').json()
	metadata = json.dumps(metadata)

	return metadata

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
