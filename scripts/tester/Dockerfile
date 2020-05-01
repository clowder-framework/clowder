FROM ubuntu:16.04

RUN apt-get update && apt-get install -y curl jq && apt-get clean && rm -rf /var/lib/apt/lists

COPY tester.sh /

CMD ["sh", "/tester.sh"]
