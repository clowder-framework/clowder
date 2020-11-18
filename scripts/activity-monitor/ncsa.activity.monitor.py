#!/usr/bin/env python

import hashlib
import logging
import os
import requests

from pyclowder.extractors import Extractor
from pyclowder.utils import CheckMessage
import pyclowder.files


class ActivityMonitorDaemon(Extractor):
    def __init__(self):
        Extractor.__init__(self)

        # parse command line and load default logging configuration
        self.setup()

        # setup logging for the exctractor
        logging.getLogger('pyclowder').setLevel(logging.DEBUG)
        logging.getLogger('__main__').setLevel(logging.DEBUG)

    # Check whether dataset already has metadata
    def check_message(self, connector, host, secret_key, resource, parameters):
        return CheckMessage.bypass

    def process_message(self, connector, host, secret_key, resource, parameters):
        logger = logging.getLogger('__main__')
        logger.debug(resource)
        logger.debug(parameters)
        logger.debug("Done.")


if __name__ == "__main__":
    extractor = ActivityMonitorDaemon()
    extractor.start()
