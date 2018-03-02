#!/bin/bash

if [ -z "$PASSWORD" ] | [ -z "$EMAIL_ADDRESS" ]; then
        echo "parameters are empty (password, emailaddress)"
        exit
fi

if [ "$MONGO_URI" == "" ]; then
    if [ -n "$MONGO_PORT_27017_TCP_PORT" ]; then
        MONGO_URI="mongodb://${MONGO_PORT_27017_TCP_ADDR}:${MONGO_PORT_27017_TCP_PORT}/clowder"
    else
        MONGO_URI="mongodb://127.0.0.1:27017/clowder"
    fi
fi

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

