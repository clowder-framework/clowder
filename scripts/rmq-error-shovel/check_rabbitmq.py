#!/usr/bin/env python3

""" RabbitMQ Error Shovel Service

Given a particular RabbitMQ instance and extractor name, this will check the error queue of that extractor for messages.
- If the dataset or file in the message exists, send the message back to the main queue.
- If it does not exist, generate a log entry and delete the message from the error queue permanently.
"""
import os
import logging
import json
import threading
import requests
import pika


# Simplified version of pyClowder's RabbitMQConnector class.
class RabbitMQMonitor():
    def __init__(self, rabbitmq_uri, extractor_name):
        self.connection = None
        self.channel = None
        self.rabbitmq_uri = rabbitmq_uri
        self.queue_basic = extractor_name
        self.queue_error = "error."+extractor_name

        self.worker = None
        self.finished = False
        self.lock = threading.Lock()
        self.messages = []

    def connect(self):
        parameters = pika.URLParameters(self.rabbitmq_uri)
        self.connection = pika.BlockingConnection(parameters)
        self.channel = self.connection.channel()

    def listen(self):
        if not self.channel:
            self.connect()

        self.listener = self.channel.basic_consume(queue=self.queue_error,
                                                   on_message_callback=self.on_message,
                                                   auto_ack=False)

        logging.getLogger(__name__).info("Starting to listen for error messages.")
        try:
            # pylint: disable=protected-access
            while self.channel and self.channel.is_open and self.channel._consumer_infos:
                self.channel.connection.process_data_events(time_limit=1)  # 1 second
                if self.worker:
                    self.process_messages(self.channel)
                    if self.is_finished():
                        self.worker = None
        except SystemExit:
            raise
        except KeyboardInterrupt:
            raise
        except GeneratorExit:
            raise
        except Exception:  # pylint: disable=broad-except
            logging.getLogger(__name__).exception("Error while consuming error messages.")
        finally:
            logging.getLogger(__name__).info("Stopped listening for error messages.")
            if self.channel and self.channel.is_open:
                try:
                    self.channel.close()
                except Exception:
                    logging.getLogger(__name__).exception("Error while closing channel.")
            self.channel = None
            if self.connection and self.connection.is_open:
                try:
                    self.connection.close()
                except Exception:
                    logging.getLogger(__name__).exception("Error while closing connection.")
            self.connection = None

    @staticmethod
    def _decode_body(body, codecs=None):
        if not codecs:
            codecs = ['utf8', 'iso-8859-1']
        # see https://stackoverflow.com/a/15918519
        for i in codecs:
            try:
                return body.decode(i)
            except UnicodeDecodeError:
                pass
        raise ValueError("Cannot decode body")

    def on_message(self, channel, method, header, body):
        """When the message is received this will check the message target.
        Any message will only be acked if the message is processed,
        or there is an exception (except for SystemExit and SystemError exceptions).
        """

        try:
            json_body = json.loads(self._decode_body(body))
            if 'routing_key' not in json_body and method.routing_key:
                json_body['routing_key'] = method.routing_key

            self.worker = threading.Thread(target=self.evaluate_message, args=(json_body,))
            self.worker.start()

        except ValueError:
            # something went wrong, move message to error queue and give up on this message immediately
            logging.getLogger(__name__).exception("Error processing message.")
            # TODO: What to do here?
            properties = pika.BasicProperties(delivery_mode=2, reply_to=header.reply_to)
            channel.basic_publish(exchange='',
                                  routing_key='error.' + self.extractor_name,
                                  properties=properties,
                                  body=body)
            channel.basic_ack(method.delivery_tag)

    def process_messages(self, channel):
        while self.messages:
            with self.lock:
                msg = self.messages.pop(0)

            if msg["type"] == 'missing':
                jbody = json.loads(self.body)
                logging.error("%s [%s] message removed." % (jbody["resource_type"], jbody["resource_id"]))
                channel.basic_ack(self.method.delivery_tag)
                with self.lock:
                    self.finished = True

            elif msg["type"] == 'resubmit':
                properties = pika.BasicProperties(delivery_mode=2, reply_to=self.header.reply_to)
                # Reset retry count to 0
                # TODO: If resource exists but retry count > some N, should we stop bouncing it back to main queue?
                jbody = json.loads(self.body)
                jbody["retry_count"] = 0
                logging.error("%s [%s] message resubmitted." % (jbody["resource_type"], jbody["resource_id"]))
                channel.basic_publish(exchange='',
                                      routing_key=self.queue_basic,
                                      properties=properties,
                                      body=json.dumps(jbody))
                channel.basic_ack(self.method.delivery_tag)
                with self.lock:
                    self.finished = True

            else:
                logging.getLogger(__name__).error("Received unknown message type [%s]." % msg["type"])

    def evaluate_message(self, jbody):
        # TODO: If dataset or file, check existence as necessary.
        logging.getLogger(__name__).info(jbody)
        host = jbody.get('host', '')
        if host == '':
            return
        elif not host.endswith('/'):
            host += '/'
        key = jbody.get("secretKey", '')
        res_type = jbody["type"]
        res_id = jbody["id"]

        logging.getLogger(__name__).info("Checking %s?key=%s" % (host, key))
        r = self.check_existence(host, key, res_type, res_id)
        resubmit = False

        if r.status_code == 200:
            # The erroneous resource exists, so resubmit to main queue
            resubmit = True
        elif r.status_code == 401:
            # Unauthorized to view resource, but it exists so resubmit (extractor might use other creds)
            logging.getLogger(__name__).error("Not authorized: %s [%s]" % (res_type, res_id))
            resubmit = True
        else:
            logging.getLogger(__name__).error("%s: %s [%s]" % (r, res_type, res_id))
            self.messages.append({
                "type": "missing",
                "resource_type": res_type,
                "resource_id": res_id
            })

        if resubmit:
            self.messages.append({
                "type": "resubmit",
                "resource_type": res_type,
                "resource_id": res_id
            })
    # Return status code of request for the resource from Clowder
    def check_existence(self, host, key, resource_type, resource_id):
        # TODO: Is there a better exists URL to use?
        clowder_url = "%sapi/%s/%s/metadata?key=%s" % (host, resource_type, resource_id, key)
        r = requests.get(clowder_url)
        return r.status_code

    def is_finished(self):
        with self.lock:
            return self.worker and not self.worker.isAlive() and self.finished and len(self.messages) == 0


if __name__ == '__main__':
    rabbitmq_uri = os.getenv('RABBITMQ_URI', 'amqp://guest:guest@localhost:5672/%2f')
    extractor_name = os.getenv('EXTRACTOR_QUEUE', 'ncsa.image.preview')
    monitor = RabbitMQMonitor(rabbitmq_uri, extractor_name)
    logging.getLogger(__name__).info("Starting to listen to "+extractor_name)
    monitor.listen()
