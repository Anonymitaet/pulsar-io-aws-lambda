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

import com.amazonaws.client.builder.AwsClientBuilder;
import com.amazonaws.services.lambda.AWSLambdaAsync;
import com.amazonaws.services.lambda.AWSLambdaAsyncClient;
import com.amazonaws.services.lambda.AWSLambdaAsyncClientBuilder;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.io.Serializable;
import java.util.Map;
import lombok.Data;
import org.apache.pulsar.io.aws.AwsCredentialProviderPlugin;
import org.apache.pulsar.io.core.annotations.FieldDoc;

/**
 * The configuration class for {@link AWSLambdaSink}.
 */
@Data
public class AWSLambdaConnectorConfig implements Serializable {
    private static final long serialVersionUID = 1L;

    @FieldDoc(
            required = false,
            defaultValue = "",
            help = "AWS Lambda end-point url. It can be found "
                    + "at https://docs.aws.amazon.com/general/latest/gr/rande.html"
    )
    private String awsEndpoint = "";

    @FieldDoc(
            required = true,
            defaultValue = "",
            help = "Appropriate aws region. E.g. us-west-1, us-west-2"
    )
    private String awsRegion;

    @FieldDoc(
            required = true,
            defaultValue = "",
            help = "The lambda function name should be invoked."
    )
    private String lambdaFunctionName;

    @FieldDoc(
            required = false,
            defaultValue = "",
            help = "Fully-Qualified class name of implementation of AwsCredentialProviderPlugin."
                    + " It is a factory class which creates an AWSCredentialsProvider that will be used by aws client."
                    + " If it is empty then aws client will create a default AWSCredentialsProvider which accepts json"
                    + " of credentials in `awsCredentialPluginParam`")
    private String awsCredentialPluginName = "";

    @FieldDoc(
            required = false,
            defaultValue = "",
            help = "json-parameters to initialize `AwsCredentialsProviderPlugin`")
    private String awsCredentialPluginParam = "";

    @FieldDoc(
            required = true,
            defaultValue = "true",
            help = "Invoke a lambda function synchronously, false to invoke asynchronously.")
    private boolean synchronousInvocation = true;

    @FieldDoc(
            required = false,
            defaultValue = "",
            help = "The successful AWS Lambda results can be written to the target "
                    + "topic when `synchronousInvocation=ture`.")
    private String successResultTopic = "";

    @FieldDoc(
            required = false,
            defaultValue = "",
            help = "The failure AWS Lambda results can be written to the target "
                    + "topic when `synchronousInvocation=ture`.")
    private String failureResultTopic = "";

    @FieldDoc(
            required = true,
            defaultValue = "10",
            help = "The maximum number of messages to send as a batch for a single Lambda function invocation,"
                    + " set to 1 to disable the batch mode.")
    private int batchSize = 10;

    public static AWSLambdaConnectorConfig load(Map<String, Object> map) throws IOException {
        ObjectMapper mapper = new ObjectMapper();
        return mapper.readValue(new ObjectMapper().writeValueAsString(map), AWSLambdaConnectorConfig.class);
    }

    public AWSLambdaAsync buildAWSLambdaAsync(AwsCredentialProviderPlugin credPlugin) {
        AWSLambdaAsyncClientBuilder builder = AWSLambdaAsyncClient.asyncBuilder();

        if (!this.getAwsEndpoint().isEmpty()) {
            builder.setEndpointConfiguration(new AwsClientBuilder.EndpointConfiguration(
                    this.getAwsEndpoint(),
                    this.getAwsRegion()));
        } else if (!this.getAwsRegion().isEmpty()) {
            builder.setRegion(this.getAwsRegion());
        }
        builder.setCredentials(credPlugin.getCredentialProvider());
        return builder.build();
    }
}
