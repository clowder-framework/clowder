FROM alpine

ENV CLOWDER_URL=""\
    CLOWDER_KEY=""\
    TARGET_FILE=""\
    SLACK_TOKEN=""\
    SLACK_CHANNEL=""\
    SLACK_USER=""

RUN apk update \
 && apk add jq \
 && apk add --no-cache curl \
 && rm -rf /var/cache/apk/*


COPY tester.sh /

CMD ["tester.sh"]
