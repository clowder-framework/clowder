#!/bin/bash

if [ -z "$PASSWORD" ] | [ -z "$EMAIL_ADDRESS" ]; then
        echo "parameters are empty (password, emailaddress)"
        exit
fi

if [ "$RABBITMQ_PORT_15672_TCP_ADDR" == "" ]; then
    RABBITMQ_PORT_15672_TCP_ADDR="rabbitmq"
fi

if [ "$RABBITMQ_PORT_15672_PORT" == "" ]; then
    RABBITMQ_PORT_15672_PORT="15672"
fi

if [ "$MONGO_PORT_27017_TCP_ADDR" == "" ]; then
    MONGO_PORT_27017_TCP_ADDR="mongo"
fi

if [ "$MONGO_PORT_27017_TCP_PORT" == "" ]; then
    MONGO_PORT_27017_TCP_PORT="27017"
fi

if [ "$CLOWDER_PORT_9000_TCP_ADDR" == "" ]; then
    CLOWDER_PORT_9000_TCP_ADDR="clowder"
fi

if [ "$CLOWDER_PORT_9000_TCP_PORT" == "" ]; then
    CLOWDER_PORT_9000_TCP_PORT="9000"
fi

if [ "$MONGO_URI" == "" ]; then
    if [ -n "$MONGO_PORT_27017_TCP_ADDR" ]; then
        MONGO_URI="mongodb://${MONGO_PORT_27017_TCP_ADDR}:${MONGO_PORT_27017_TCP_PORT}/clowder"
    else
        MONGO_URI="mongodb://127.0.0.1:27017/clowder"
    fi
fi

# wait rabbitmq to be ready
until nc -z ${RABBITMQ_PORT_15672_TCP_ADDR} ${RABBITMQ_PORT_15672_PORT}
do
    echo "wait for rabbitmq to be ready" ${RABBITMQ_PORT_15672_TCP_ADDR} ${RABBITMQ_PORT_15672_PORT}
    sleep 1
done

echo "rabbitmq connected"

# wait mongo to be ready
until nc -z ${MONGO_PORT_27017_TCP_ADDR} ${MONGO_PORT_27017_TCP_PORT}
do
    echo "wait for mongo to be ready" ${MONGO_PORT_27017_TCP_ADDR} ${MONGO_PORT_27017_TCP_PORT}
    sleep 1
done

echo "mongodb connected"

# wait clowder to be ready
until nc -z ${CLOWDER_PORT_9000_TCP_ADDR} ${CLOWDER_PORT_9000_TCP_PORT}
do
    echo "wait for clowder to be ready" ${CLOWDER_PORT_9000_TCP_ADDR} ${CLOWDER_PORT_9000_TCP_PORT}
    sleep 1
done

while ! curl -s http://${CLOWDER_PORT_9000_TCP_ADDR}:${CLOWDER_PORT_9000_TCP_PORT}/api/status; do
  echo "clowder is not ready, waiting one second" >&2
  sleep 1
done |
  until grep -q "services.mongodb.MongoDBSecureSocialUserService"; do
    echo "Waiting for pattern to match"
  done > /dev/null

echo "rabbitmq, mongo, clowder are ready"

emailaddress=$EMAIL_ADDRESS

firstname="Normal"
admin=false
ADMIN=$(echo $ADMIN | tr '[:upper:]' '[:lower:]')
if [[ $ADMIN == "true" ]]; then
    admin=true
    firstname="Admin"
fi

if [ ! -z "$FIRST_NAME" ]; then
        firstname=$FIRST_NAME
fi

lastname="User"
if [ ! -z "$LAST_NAME" ]; then
        lastname=$LAST_NAME
fi
IFS='%'
fullname="$firstname $lastname"

echo "+++++++++++++++++++++++++++++++++++++"
echo "Add User Account into Clowder"
echo ""
echo $(mongo --version)
echo ""
echo "++++++++++++++++++++++++++++++++++++++"

while [ $(netstat -plnt | grep ':9000')]; do
        sleep 1
done

echo "clowder is starting"

query=$(mongo $MONGO_URI --eval "db.social.users.find({email: '"$emailaddress"', \"termsOfServices.accepted\": true}, {identityId:1})")

if echo $query | grep -q "$emailaddress"; then
        echo "account found"
else
        echo "account not found!"
	hasher=$(python3 /usr/local/bin/passwd)
	mongo $MONGO_URI --eval "db.social.users.remove({email: '"$emailaddress"'})"
        mongo $MONGO_URI --eval "db.social.users.insert(
        {
                identityId: {
			userId: '"$emailaddress"', 
			providerId: \"userpass\"
		}, 
                _typeHint: \"models.ClowderUser\", 
                firstName: '"$firstname"', 
                lastName:  '"$lastname"', 
                fullName:  '"$fullname"', 
                email:     '"$emailaddress"', 
                authMethod: {
			_typeHint: \"securesocial.core.AuthenticationMethod\", 
			method: \"userPassword\"
		}, 
                passwordInfo: {
			_typeHint: \"securesocial.core.PasswordInfo\", 
	                hasher: \"bcrypt\", 
        	        password: '"$hasher"'
		},
                status : \"Active\",
                serverAdmin: $admin, 
                termsOfServices: {
			accepted: true, 
			acceptedDate: ISODate(\"2020-10-25T20:17:02.591Z\"), 
			acceptedVersion: \"2016-06-06\"}
        });db.fsyncLock();db.fsyncUnlock();"

unset IFS
fi

