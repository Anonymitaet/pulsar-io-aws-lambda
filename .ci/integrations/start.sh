#!/bin/bash
#
# Licensed to the Apache Software Foundation (ASF) under one
# or more contributor license agreements.  See the NOTICE file
# distributed with this work for additional information
# regarding copyright ownership.  The ASF licenses this file
# to you under the Apache License, Version 2.0 (the
# "License"); you may not use this file except in compliance
# with the License.  You may obtain a copy of the License at
#
#   http://www.apache.org/licenses/LICENSE-2.0
#
# Unless required by applicable law or agreed to in writing,
# software distributed under the License is distributed on an
# "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
# KIND, either express or implied.  See the License for the
# specific language governing permissions and limitations
# under the License.
#

set -e

SRC_DIR=$(git rev-parse --show-toplevel)
cd $SRC_DIR

IMAGE_NAME=pulsar-io-aws-lambda-test:latest
MVN_VERSION=`${SRC_DIR}/.ci/versions/get-project-version.py`
CONNECTOR_CONFIG=${CONNECTOR_CONFIG:-"test-pulsar-io-aws-lambda-sink"}

docker build -t ${IMAGE_NAME} .

docker network create aws-lambda-test

docker kill pulsar-io-aws-lambda-test || true
docker run  --network aws-lambda-test -d --rm --name pulsar-io-aws-lambda-test \
            -p 8080:8080 \
            -p 6650:6650 \
            -p 8443:8843 \
            -p 6651:6651 \
            ${IMAGE_NAME}

PULSAR_ADMIN="docker exec -d pulsar-io-aws-lambda-test /pulsar/bin/pulsar-admin"

echo "-- Wait for Pulsar service to be ready"
until curl http://localhost:8080/metrics > /dev/null 2>&1 ; do sleep 1; done

echo "-- Pulsar service ready to test"

docker kill localstack || true
rm -rf ${SRC_DIR}/.ci/integrations/.localstack || true
docker run  --network aws-lambda-test -d --rm --name localstack \
            -p 4566:4566 \
            -e SERVICES=lambda,iam \
            -e DEFAULT_REGION=us-east-1 \
            -e AWS_DEFAULT_REGION=us-east-1 \
            -e AWS_ACCESS_KEY_ID=test \
            -e AWS_SECRET_ACCESS_KEY=test \
            -e LAMBDA_REMOTE_DOCKER=false \
            -e MAIN_CONTAINER_NAME=localstack \
            -e HOST_TMP_FOLDER=${SRC_DIR}/.ci/integrations/.localstack \
            -e HOSTNAME_EXTERNAL=localstack \
            -e HOSTNAME=localstack \
            -e DEBUG=1 \
            -e LAMBDA_DOCKER_NETWORK=aws-lambda-test \
            -v ${SRC_DIR}/.ci/integrations/test-function:/tmp/test-function \
            -v /var/run/docker.sock:/var/run/docker.sock \
            -v ${SRC_DIR}/.ci/integrations/.localstack:/tmp/localstack \
            localstack/localstack:latest

sudo echo "127.0.0.1 localstack" | sudo tee -a /etc/hosts
echo "-- Wait for localstack service to be ready"
until $(curl --silent --fail http://localstack:4566/health | grep "\"lambda\": \"running\"" > /dev/null);  do sleep 1; done
echo "-- localstack service ready"

echo "-- create test lambda function"
docker exec -d localstack aws --endpoint-url=http://localhost:4566 iam create-role --role-name lambda-ex --assume-role-policy-document '{"Version": "2012-10-17","Statement": [{ "Effect": "Allow", "Principal": {"Service": "lambda.amazonaws.com"}, "Action": "sts:AssumeRole"}]}'
docker exec -d localstack aws --endpoint-url=http://localhost:4566 lambda create-function --function-name test-function --zip-file fileb:///tmp/test-function/function.zip --handler index.handler --runtime nodejs12.x --role="lambda-ex"
docker exec localstack aws --endpoint-url=http://localhost:4566 lambda list-functions --max-items 10
echo "-- test lambda function is ready to test"
docker ps

# run connector
echo "-- run pulsar-io-aws-lambda sink connector"
docker exec -d pulsar-io-aws-lambda-test cp /pulsar-io-aws-lambda/target/pulsar-io-aws-lambda-${MVN_VERSION}.nar /pulsar/connectors/pulsar-io-aws-lambda-${MVN_VERSION}.nar
$PULSAR_ADMIN sinks reload
sleep 60s
$PULSAR_ADMIN sinks create -t aws-lambda \
        --tenant public --namespace default --name test-aws-lambda-sink \
        --sink-config-file /pulsar-io-aws-lambda/.ci/${CONNECTOR_CONFIG}.yaml \
        -i test-aws-lambda-sink-topic

echo "-- ready to do integration tests"