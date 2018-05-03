#!/bin/bash

rm -f /home/clowder/RUNNING_PID
exec /home/clowder/bin/clowder -DMONGOUPDATE=1 -DPOSTGRESUPDATE=1 $*
