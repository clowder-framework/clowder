#!/bin/bash
set -e

# rabbitmq
if [ "$RABBITMQ_URI" == "" ]; then
    if [ -n "$RABBITMQ_PORT_5672_TCP_PORT" ]; then
        RABBITMQ_URI="amqp://guest:guest@${RABBITMQ_PORT_5672_TCP_ADDR}:${RABBITMQ_PORT_5672_TCP_PORT}/${RABBITMQ_VHOST}"
    fi
fi
if [ "$RABBITMQ_MGMT_PORT" == "" ]; then
    if [ -n "$RABBITMQ_PORT_15672_TCP_PORT" ]; then
        RABBITMQ_MGMT_PORT="$RABBITMQ_PORT_15672_TCP_PORT"
    fi
fi

# mongo
if [ "$MONGO_URI" == "" ]; then
    if [ -n "$MONGO_PORT_27017_TCP_PORT" ]; then
        MONGO_URI="mongodb://${MONGO_PORT_27017_TCP_ADDR}:${MONGO_PORT_27017_TCP_PORT}/clowder"
    else
        MONGO_URI="mongodb://127.0.0.1:27017/clowder"
    fi
fi

# elasticsearch
if [ "$ELASTICSEARCH_SERVER" == "" ]; then
    if [ -n "$ELASTICSEARCH_PORT_9300_TCP_ADDR" ]; then
        ELASTICSEARCH_SERVER="$ELASTICSEARCH_PORT_9300_TCP_ADDR"
    fi
fi
if [ "$ELASTICSEARCH_PORT" == "" ]; then
    if [ -n "$ELASTICSEARCH_PORT_9300_TCP_PORT" ]; then
        ELASTICSEARCH_PORT="$ELASTICSEARCH_PORT_9300_TCP_PORT"
    fi
fi

# some helper functions

# add/replace plugin if variable is non empty
# $1 = variable to check if defined
# $2 = index of plugin
# $3 = plugin class
function fix_plugin() {
    if [ "$2" == "" ]; then return 0; fi
    if [ "$3" == "" ]; then return 0; fi

    if [ -e /home/clowder/clowder/custom/play.plugins ]; then
        mv /home/clowder/clowder/custom/play.plugins /home/clowder/clowder/custom/play.plugins.old
        grep -v ":$2" /home/clowder/clowder/custom/play.plugins.old > /home/clowder/clowder/custom/play.plugins
        rm /home/clowder/clowder/custom/play.plugins.old
    fi
    if [ "$1" != "" ]; then
        echo "$2:$3" >> /home/clowder/clowder/custom/play.plugins
    fi
}

# add/replace if variable is non empty
# $1 = variable to replace/remove
# $2 = new value to set
# $3 = additional variable to remove
function fix_conf() {
    local query
    if [ "$1" == "" ]; then return 0; fi

    if [ -e /home/clowder/clowder/custom/custom.conf ]; then
        if [ "$3" == "" ]; then
            query="$1"
        else
            query="$1|$3"
        fi

        mv /home/clowder/clowder/custom/custom.conf /home/clowder/clowder/custom/custom.conf.old
        grep -v "^(${query})=" /home/clowder/clowder/custom/custom.conf.old > /home/clowder/clowder/custom/custom.conf
        rm /home/clowder/clowder/custom/custom.conf.old
    fi

    if [ "$2" != "" ]; then
        echo "$1=\"$2\"" >> /home/clowder/clowder/custom/custom.conf
    fi
}

# start server if asked
if [ "$1" = 'server' ]; then
    # admins
    if [ "$CLOWDER_ADMINS" == "" ]; then
        fix_conf   "registerThroughAdmins" "false"
        fix_conf   "initialAdmins" ""
    else
        fix_conf   "registerThroughAdmins" "true"
        fix_conf   "initialAdmins" "$CLOWDER_ADMINS"
    fi

    # rabbitmq
    fix_plugin "$RABBITMQ_URI" "9992" "services.RabbitmqPlugin"
    fix_conf   "clowder.rabbitmq.uri" "$RABBITMQ_URI" "medici2.rabbitmq.uri"
    fix_conf   "clowder.rabbitmq.exchange" "$RABBITMQ_EXCHANGE" "medici2.rabbitmq.exchange"
    fix_conf   "clowder.rabbitmq.managmentPort" "$RABBITMQ_MGMT_PORT" "medici2.rabbitmq.managmentPort"

    # mongo
    fix_conf   "mongodbURI" "$MONGO_URI"

    # smtp
    fix_conf   "smtp.host" "$SMTP_HOST"
    if [ "$SMTP_HOST" == "" ]; then
        fix_conf   "smtp.mock" "true"
    else
        fix_conf   "smtp.mock" "false"
    fi

    # elasticsearch
    fix_plugin "$ELASTICSEARCH_SERVER" "10700" "services.ElasticsearchPlugin"
    fix_conf   "elasticsearchSettings.clusterName" "$ELASTICSEARCH_CLUSTERNAME"
    fix_conf   "elasticsearchSettings.serverAddress" "$ELASTICSEARCH_SERVER"
    fix_conf   "elasticsearchSettings.serverPort" "$ELASTICSEARCH_PORT"

    # start clowder
    /bin/rm -f /home/clowder/clowder/RUNNING_PID
    /bin/mkdir -p /home/clowder/clowder/custom
    exec /home/clowder/clowder/bin/clowder -DMONGOUPDATE=1 -DPOSTGRESUPDATE=1 -Dapplication.context=$CLOWDER_CONTEXT
fi

exec "$@"
