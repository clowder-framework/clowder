FROM ubuntu:16.04


# environemnt variables
ENV CLOWDER_URL=${CLOWDER_URL} \
    CLOWDER_KEY=${CLOWDER_KEY} \
    TARGET_FILE=${TARGET_FILE} \
    SLACK_TOKEN=${SLACK_TOKEN} \
    SLACK_CHANNEL=${SLACK_CHANNEL} \
    SLACK_USER=${SLACK_USER}


RUN apt-get update && apt-get install -y curl jq && apt-get clean && rm -rf /var/lib/apt/lists

COPY tester.sh /

CMD ["sh", "/tester.sh"]
