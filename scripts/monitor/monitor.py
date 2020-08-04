#!/usr/bin/env python

import datetime
import http.server
import json
import logging
import os
import threading
import time
import urllib.parse

import pika
import requests

# parameters to connect to RabbitMQ
rabbitmq_uri = os.getenv('RABBITMQ_URI', 'amqp://guest:guest@localhost/%2F')
rabbitmq_mgmt_port = os.getenv('RABBITMQ_MGMT_PORT', '15672')
rabbitmq_mgmt_path = os.getenv('RABBITMQ_MGMT_PATH', '/')
rabbitmq_mgmt_url = os.getenv('RABBITMQ_MGMT_URL', '')
rabbitmq_username = None
rabbitmq_password = None

# list of all extractors.
extractors = {}

# frequency with which the counts of the queue is updated in seconds
update_frequency = 10

# number of seconds before a consumer is removed
extractor_remove = 10 * 60 # needs to be twice or more than heartbeat

# ----------------------------------------------------------------------
# WEB SERVER
# ----------------------------------------------------------------------
class MyServer(http.server.SimpleHTTPRequestHandler):
    """
    Handles the responses from the web server. Only response that is
    handled is a GET that will return all known extractors.
    """
    def do_GET(self):
        self.path = os.path.basename(self.path)
        if self.path == '':
            self.path = '/'

        if self.path.startswith('extractors.json'):
            self.send_response(200)
            self.send_header('Content-type', 'application/json')
            self.end_headers()
            self.wfile.write(bytes(json.dumps(extractors), 'utf-8'))
        else:
            super().do_GET()

def http_server(host_port=9999):
    """
    Start a webserver to return all extractors that registered since this
    application started.
    """
    server = http.server.HTTPServer(("", host_port), MyServer)
    try:
        server.serve_forever()
    except:
        logging.exception("Error in http server")
        sys.exit(1)
    finally:
        server.server_close()


# ----------------------------------------------------------------------
# MESSAGES IN QUEUES
# ----------------------------------------------------------------------
def get_mgmt_queue_messages(queue):
    global rabbitmq_username, rabbitmq_password, rabbitmq_mgmt_url
    try:
        response = requests.get(rabbitmq_mgmt_url + queue, auth=(rabbitmq_username, rabbitmq_password), timeout=5)
        if response.status_code == 404:
            # queue does not exist, so we assume 0 messages
            return 0
        response.raise_for_status()
        return response.json()['messages']
    except Exception:
        logging.exception("Error getting list of messages in %s" % queue)
        return 0


def update_counts():
    global extractors, update_frequency, extractor_remove

    while True:
        try:
            removetime  = (datetime.datetime.now() - datetime.timedelta(seconds=extractor_remove)).isoformat()
            for versions in extractors.values():
                for extractor in versions.values():
                    # use management api to get counts
                    old_waiting = get_mgmt_queue_messages(extractor['queue'])
                    new_waiting = get_mgmt_queue_messages('extractors.' + extractor['queue'])
                    errors = get_mgmt_queue_messages('error.' + extractor['queue'])

                    # update message counts
                    extractor['messages'] = {
                        'queues': {
                            'total': old_waiting + new_waiting,
                            'direct': new_waiting,
                            'topic': old_waiting
                        },
                        'error': errors
                    }

                    # clean up old extractors
                    extractors_found = extractor.get('extractors', {})
                    delete = [id for id, c in extractors_found.items() if c['last_seen'] < removetime ]
                    for id in delete:
                        del extractors_found[id]
                    extractor['consumers'] = len(extractors_found)

            time.sleep(update_frequency)
        except:
            logging.exception("Error updating extractors.")


# ----------------------------------------------------------------------
# EXTRACTOR HEARTBEATS
# ----------------------------------------------------------------------
def callback(ch, method, properties, body):
    """
    A heartbeat of an extractor is received.
    """
    global extractors

    data = json.loads(body.decode('utf-8'))
    data['updated'] = datetime.datetime.now().isoformat()
    if 'id' not in data and 'extractor_info' not in data and 'queue' not in data:
        logging.error("missing fields in json : %r " % body)
        return

    extractor_info = data['extractor_info']

    if extractor_info['name'] not in extractors:
        extractors[extractor_info['name']] = {}

    if extractor_info['version'] not in extractors[extractor_info['name']]:
        extractors[extractor_info['name']][extractor_info['version']] = {
            'extractor_info': extractor_info,
            'queue': data['queue'],
            'extractors': {},
            'consumers': 0
        }
    extractor = extractors[extractor_info['name']][extractor_info['version']]

    if data['id'] not in extractor['extractors']:
        extractor['consumers'] = extractor['consumers'] + 1
    extractor['extractors'][data['id']] = {
        'last_seen': datetime.datetime.now().isoformat(),
    }

    if extractor['queue'] != data['queue']:
        logging.error("mismatched queue names %s != %s." % (data['queue'], extractor['queue']))
        extractor['queue'] = data['queue']


def rabbitmq_monitor():
    """
    Create a connection with RabbitMQ and wait for heartbeats. This
    will run continuously. This will run as the main thread. If this
    is stopped the appliation will stop.
    """
    global rabbitmq_mgmt_url, rabbitmq_mgmt_port, rabbitmq_mgmt_path, rabbitmq_username, rabbitmq_password

    params = pika.URLParameters(rabbitmq_uri)
    connection = pika.BlockingConnection(params)

    # create management url
    if not rabbitmq_mgmt_url:
        if params.ssl_options:
            rabbitmq_mgmt_protocol = 'https://'
        else:
            rabbitmq_mgmt_protocol = 'http://'
        rabbitmq_mgmt_url = "%s%s:%s%sapi/queues/%s/" % (rabbitmq_mgmt_protocol, params.host, rabbitmq_mgmt_port,
                                                         rabbitmq_mgmt_path,
                                                         urllib.parse.quote_plus(params.virtual_host))
        rabbitmq_username = params.credentials.username
        rabbitmq_password = params.credentials.password

    # connect to channel
    channel = connection.channel()

    # create extractors exchange for fanout
    channel.exchange_declare(exchange='extractors', exchange_type='fanout', durable=True)

    # create anonymous queue
    result = channel.queue_declare('', exclusive=True)
    channel.queue_bind(exchange='extractors', queue=result.method.queue)

    # listen for messages
    channel.basic_consume(on_message_callback=callback, queue=result.method.queue, auto_ack=True)

    channel.start_consuming()


# ----------------------------------------------------------------------
# MAIN
# ----------------------------------------------------------------------
if __name__ == "__main__":
    logging.basicConfig(format='%(asctime)-15s [%(threadName)-15s] %(levelname)-7s :'
                               ' %(name)s - %(message)s',
                        level=logging.INFO)
    logging.getLogger('requests.packages.urllib3.connectionpool').setLevel(logging.WARN)

    thread = threading.Thread(target=http_server)
    thread.setDaemon(True)
    thread.start()

    thread = threading.Thread(target=update_counts)
    thread.setDaemon(True)
    thread.start()

    rabbitmq_monitor()
