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

import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;
import org.apache.pulsar.functions.api.Record;
import org.junit.Test;

/**
 * Unit test {@link AWSLambdaBytesSink}.
 */
public class AWSLambdaSinkTest {
    /*
     * Test Case: AWSLambdaSink should connect to AWS Lambda with correct configs
     *
     */
    @Test
    public void testAWSLambdaSinkConnectToAWSLambda() {
        Map<String, Object> properties = getTestConfigHashMap();

        AWSLambdaBytesSink sink = new AWSLambdaBytesSink();
        try {
            sink.open(properties, null);
        } catch (Exception e) {
            assertNull("Connect to AWS Lambda should not get exception", e);
        }
    }

    public static Map<String, Object> getTestConfigHashMap() {
        Map<String, Object> properties = new HashMap<String, Object>();
        properties.put("awsRegion", "us-east-1");
        properties.put("lambdaFunctionName", "test-function");
        properties.put("awsEndpoint", "http://localhost:4566");
        properties.put("awsCredentialPluginParam", "{\"accessKey\":\"myKey\",\"secretKey\":\"my-Secret\"}");
        return properties;
    }

    @Test
    public void testConvertToLambdaPayloadWithStringSink() {
        AWSLambdaAbstractSink<String> sink = new AWSLambdaAbstractSink<String>();
        Record<String> message = new Record<String>() {
            @Override
            public String getValue() {
                return "hello";
            }

        };

        String result;
        try {
            result = new String(sink.convertToLambdaPayload(message), StandardCharsets.UTF_8);
            assertNotNull(result);
            assertTrue(result.contains("hello"));
            assertTrue(result.contains("value"));
        } catch (Exception e) {
            e.printStackTrace();
        }

    }

    @Test
    public void testConvertToLambdaPayloadWithBytesSink() {
        AWSLambdaAbstractSink<byte[]> sink = new AWSLambdaAbstractSink<byte[]>();
        Record<byte[]> message = new Record<byte[]>() {
            @Override
            public byte[] getValue() {
                return "hello".getBytes(StandardCharsets.UTF_8);
            }

        };

        String result;
        try {
            result = new String(sink.convertToLambdaPayload(message), StandardCharsets.UTF_8);
            assertNotNull(result);
            assertTrue(result.contains("value"));
        } catch (Exception e) {
            e.printStackTrace();
        }

    }
}
