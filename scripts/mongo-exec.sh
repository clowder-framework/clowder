#!/bin/sh

# Either make sure mongo is in your $PATH,
# or edit the following line to contain the full pathname of
# the "mongo" executable.
echo "mongo --quiet --norc testbr <<EOF\n$1\nEOF\n" > /tmp/mongo-tmp.sh \
  && sh /tmp/mongo-tmp.sh
