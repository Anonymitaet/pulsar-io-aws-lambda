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
package org.apache.pulsar.ecosystem.io.aws.lambda;

import com.amazonaws.services.lambda.AWSLambdaAsync;
import com.amazonaws.services.lambda.model.InvocationType;
import com.amazonaws.services.lambda.model.InvokeRequest;
import com.amazonaws.services.lambda.model.InvokeResult;
import com.amazonaws.services.lambda.model.RequestTooLargeException;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import lombok.Getter;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;
import org.apache.pulsar.functions.api.Record;
import org.apache.pulsar.io.aws.AbstractAwsConnector;
import org.apache.pulsar.io.aws.AwsCredentialProviderPlugin;
import org.apache.pulsar.io.core.Sink;
import org.apache.pulsar.io.core.SinkContext;

/**
 * Abstract Class for pulsar sink connector to AWS Lambda.
 */
@Slf4j
public class AWSLambdaAbstractSink<T> extends AbstractAwsConnector implements Sink<T> {
    public static final long DEFAULT_INVOKE_TIMEOUT_MS = 5 * 60 * 1000L;
    private static final int MAX_SYNC_PAYLOAD_SIZE_BYTES = (6 * 1024 * 1024);
    private static final int MAX_ASYNC_PAYLOAD_SIZE_BYTES = (256 * 1024);

    private static final String METRICS_TOTAL_SUCCESS = "_lambda_sink_total_success_";
    private static final String METRICS_TOTAL_FAILURE = "_lambda_sink_total_failure_";

    @Getter
    @Setter
    private AWSLambdaConnectorConfig config;

    @Getter
    private AWSLambdaAsync client;

    @Getter
    private String functionName;

    private SinkContext sinkContext;

    public void prepareAWSLambda() {
        if (config == null || client != null) {
            throw new IllegalStateException("Connector is already open");
        }

        AwsCredentialProviderPlugin credentialsProvider = createCredentialProvider(
                config.getAwsCredentialPluginName(),
                config.getAwsCredentialPluginParam());

        client = config.buildAWSLambdaAsync(credentialsProvider);
        functionName = config.getLambdaFunctionName();
    }

    @Override
    public void open(Map<String, Object> map, SinkContext sinkContext) throws Exception {
        this.sinkContext = sinkContext;
        setConfig(AWSLambdaConnectorConfig.load(map));
        prepareAWSLambda();
    }

    @Override
    public void write(Record<T> record) {
        try {
            InvokeResult result = invoke(record);
            if (result != null && result.getStatusCode() < 300 && result.getStatusCode() >= 200) {
                record.ack();
                if (sinkContext != null) {
                    sinkContext.recordMetric(METRICS_TOTAL_SUCCESS, 1);
                }
            } else {
                log.error("failed send message to AWS Lambda function {}.", getFunctionName());
                record.fail();
                if (sinkContext != null) {
                    sinkContext.recordMetric(METRICS_TOTAL_FAILURE, 1);
                }
            }
        } catch (Exception e) {
            log.error("failed send message to AWS Lambda function {} with error {}.", getFunctionName(), e);
            record.fail();
            if (sinkContext != null) {
                sinkContext.recordMetric(METRICS_TOTAL_FAILURE, 1);
            }
        }
    }

    public InvokeResult invoke(final Record<T> record) throws
            InterruptedException, ExecutionException, TimeoutException, JsonProcessingException {
        final InvocationType type = getConfig().isSynchronousInvocation()
                ? InvocationType.RequestResponse : InvocationType.Event;

        final byte[] payload = convertToLambdaPayload(record);

        final InvokeRequest request = new InvokeRequest()
                .withInvocationType(type)
                .withFunctionName(getFunctionName())
                .withPayload(ByteBuffer.wrap(payload));

        final Future<InvokeResult> futureResult = getClient().invokeAsync(request);

        try {
            return futureResult.get(DEFAULT_INVOKE_TIMEOUT_MS, TimeUnit.MILLISECONDS);
        } catch (RequestTooLargeException e) {
            if (getConfig().isSynchronousInvocation()
                    && payload.length > MAX_SYNC_PAYLOAD_SIZE_BYTES) {
                log.error("record payload size {} is exceed the max payload "
                        + "size for synchronous lambda function invoke.", payload.length);

            } else if (!getConfig().isSynchronousInvocation()
                    && payload.length > MAX_ASYNC_PAYLOAD_SIZE_BYTES) {
                log.error("record payload size {} is exceed the max payload "
                        + "size for asynchronous lambda function invoke.", payload.length);

            }
            throw e;
        } catch (final InterruptedException | ExecutionException | TimeoutException e) {
            log.error(e.getLocalizedMessage(), e);
            throw e;
        }
    }

    @Override
    public void close() {
        if (getClient() != null) {
            getClient().shutdown();
        }
    }

    public byte[] convertToLambdaPayload(Record<T> message) throws JsonProcessingException {
        final ObjectMapper mapper = new ObjectMapper();
        mapper.configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);
        mapper.configure(DeserializationFeature.READ_UNKNOWN_ENUM_VALUES_AS_NULL, true);
        mapper.setSerializationInclusion(JsonInclude.Include.NON_NULL);
        try {
            return mapper.writeValueAsBytes(message);
        } catch (JsonProcessingException e) {
            try {
                // check if message value is valid json
                mapper.readTree(new String((byte[]) message.getValue(), StandardCharsets.UTF_8));
                return (byte[]) message.getValue();
            } catch (Exception ex) {
                // create InvokePayload json string
                InvokePayload payload = new InvokePayload();
                if (message.getValue() != null) {
                    payload.setValue(new String((byte[]) message.getValue(), StandardCharsets.UTF_8));
                }
                if (message.getKey().isPresent()) {
                    payload.setKey(message.getKey().get());
                }
                if (message.getDestinationTopic().isPresent()) {
                    payload.setDestinationTopic(message.getDestinationTopic().get());
                }
                if (message.getEventTime().isPresent()) {
                    payload.setEventTime(message.getEventTime().get());
                }
                if (message.getPartitionId().isPresent()) {
                    payload.setPartitionId(message.getPartitionId().get());
                }
                if (message.getTopicName().isPresent()) {
                    payload.setTopicName(message.getTopicName().get());
                }
                if (message.getProperties() != null && !message.getProperties().isEmpty()) {
                    Map<String, String> p = new HashMap<>();
                    message.getProperties().forEach(p::put);
                    payload.setProperties(p);
                }

                return mapper.writeValueAsBytes(payload);
            }
        }
    }
}

