/*
 * Copyright 2010-2019 Amazon.com, Inc. or its affiliates. All Rights Reserved.
 *
 * Licensed under the Apache License, Version 2.0 (the "License").
 * You may not use this file except in compliance with the License.
 * A copy of the License is located at
 *
 *  http://aws.amazon.com/apache2.0
 *
 * or in the "license" file accompanying this file. This file is distributed
 * on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either
 * express or implied. See the License for the specific language governing
 * permissions and limitations under the License.
 */

package software.amazon.awssdk.http.crt;

import software.amazon.awssdk.annotations.SdkPublicApi;
import software.amazon.awssdk.crt.http.HttpConnection;
import software.amazon.awssdk.http.SdkHttpConfigurationOption;
import software.amazon.awssdk.http.async.AsyncExecuteRequest;
import software.amazon.awssdk.http.async.SdkAsyncHttpClient;
import software.amazon.awssdk.http.crt.internal.AwsCrtConfiguration;
import software.amazon.awssdk.utils.AttributeMap;
import software.amazon.awssdk.utils.Logger;

import java.net.URI;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;

/**
 * A class that wraps the AWS Common Runtime Http Client Library through JNI.
 *
 * <p>This can be created via {@link #builder()}</p>
 */
@SdkPublicApi
public class AwsCrtAsyncHttpClient implements SdkAsyncHttpClient {
    private static final Logger log = Logger.loggerFor(AwsCrtAsyncHttpClient.class);

    private final AwsCrtConfiguration configuration;
    private final Map<URI, HttpConnection> connections;


    AwsCrtAsyncHttpClient(AwsCrtAsyncHttpClient.DefaultBuilder builder, AttributeMap serviceDefaultsMap) {
        this.configuration = new AwsCrtConfiguration(serviceDefaultsMap);
        this.connections = new ConcurrentHashMap<>();
    }

    @Override
    public CompletableFuture<Void> execute(AsyncExecuteRequest request) {
        // TODO
        return null;
    }

    @Override
    public void close() {
        // TODO
    }

    public static Builder builder() {
        return new DefaultBuilder();
    }

    /**
     * Builder that allows configuration of the AWS CRT HTTP implementation. Use {@link #builder()} to configure and construct
     * a AWS CRT Connection.
     */
    public interface Builder extends SdkAsyncHttpClient.Builder<AwsCrtAsyncHttpClient.Builder> {

    }

    private static final class DefaultBuilder implements Builder {
        private final AttributeMap.Builder standardOptions = AttributeMap.builder();

        private DefaultBuilder() {
        }

        @Override
        public SdkAsyncHttpClient buildWithDefaults(AttributeMap serviceDefaults) {
            return new AwsCrtAsyncHttpClient(this, standardOptions.build()
                    .merge(serviceDefaults)
                    .merge(SdkHttpConfigurationOption.GLOBAL_HTTP_DEFAULTS));

        }
    }
}
