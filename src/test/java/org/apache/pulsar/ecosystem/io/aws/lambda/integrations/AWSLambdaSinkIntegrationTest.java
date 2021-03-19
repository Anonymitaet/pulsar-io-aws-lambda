/**
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */
package org.apache.pulsar.ecosystem.io.aws.lambda.integrations;

import lombok.Cleanup;
import org.apache.pulsar.client.admin.PulsarAdmin;
import org.apache.pulsar.client.api.Producer;
import org.apache.pulsar.client.api.PulsarClient;
import org.apache.pulsar.client.api.Schema;
import org.apache.pulsar.common.policies.data.SinkStatus;
import org.apache.pulsar.ecosystem.io.aws.lambda.AWSLambdaBytesSink;
import org.junit.Assert;
import org.junit.Test;

/**
 * Integration test {@link AWSLambdaBytesSink}.
 */
public class AWSLambdaSinkIntegrationTest {
    private static final String PULSAR_SINK_NAME = "test-aws-lambda-sink";
    private static final String PULSAR_TOPIC_NAME = "test-aws-lambda-sink-topic";
    private static final String PULSAR_PRODUCER_NAME = "test-aws-lambda-sink-producer";
    private static final String MSG = "hello-message-";

    @Test
    public void testLambdaSinkPushMsgToAWSLambda() {
        try {
            // send test messages to Pulsar
            produceMessagesToPulsar();

            // test if sink pushed message to aws lambda successfully
            validateSinkResult();
        } catch (Exception e) {
            Assert.assertNull("produce test messages to pulsar should not throw exception", e);
        }
    }

    public void produceMessagesToPulsar() throws Exception {
        @Cleanup
        PulsarClient pulsarClient = PulsarClient.builder()
                .serviceUrl("http://localhost:8080")
                .build();

        @Cleanup
        Producer<String> pulsarProducer = pulsarClient.newProducer(Schema.STRING)
                .topic(PULSAR_TOPIC_NAME)
                .producerName(PULSAR_PRODUCER_NAME)
                .create();

        for (int i = 0; i < 100; i++) {
            pulsarProducer.newMessage().value(MSG + i).send();
        }

        pulsarProducer.close();
        pulsarClient.close();
    }

    public void validateSinkResult() throws Exception {
        @Cleanup
        PulsarAdmin pulsarAdmin = PulsarAdmin.builder()
                .serviceHttpUrl("http://localhost:8080")
                .build();

        SinkStatus status = pulsarAdmin.sinks().getSinkStatus("public", "default", PULSAR_SINK_NAME);
        Assert.assertEquals(1, status.getNumRunning());
        Assert.assertNotNull(status.getInstances().get(0));
        Assert.assertEquals(100, status.getInstances().get(0).getStatus().numReadFromPulsar);
        Assert.assertEquals(100, status.getInstances().get(0).getStatus().numWrittenToSink);

        pulsarAdmin.close();
    }

}
