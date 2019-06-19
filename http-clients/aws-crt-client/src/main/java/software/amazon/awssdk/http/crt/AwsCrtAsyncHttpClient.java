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

import static software.amazon.awssdk.utils.FunctionalUtils.invokeSafely;

import java.net.URI;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;

import software.amazon.awssdk.annotations.SdkPublicApi;
import software.amazon.awssdk.crt.http.HttpConnection;
import software.amazon.awssdk.crt.http.HttpHeader;
import software.amazon.awssdk.crt.http.HttpRequest;
import software.amazon.awssdk.crt.io.ClientBootstrap;
import software.amazon.awssdk.crt.io.SocketOptions;
import software.amazon.awssdk.crt.io.TlsContext;
import software.amazon.awssdk.http.SdkHttpConfigurationOption;
import software.amazon.awssdk.http.SdkHttpRequest;
import software.amazon.awssdk.http.async.AsyncExecuteRequest;
import software.amazon.awssdk.http.async.SdkAsyncHttpClient;
import software.amazon.awssdk.http.crt.internal.AwsCrtAsyncRequestResponseAdapter;
import software.amazon.awssdk.utils.AttributeMap;
import software.amazon.awssdk.utils.Logger;
import software.amazon.awssdk.utils.Validate;

@SdkPublicApi
public class AwsCrtAsyncHttpClient implements SdkAsyncHttpClient {

    private static final Logger log = Logger.loggerFor(AwsCrtAsyncHttpClient.class);
    private static final String CLIENT_NAME = "AwsCommonRuntime";
    private static final int DEFAULT_STREAM_WINDOW_SIZE = 4 * 1024 * 1024; // Queue up to 4 MB of Http Body Bytes
    private final Map<URI, HttpConnection> connections = new ConcurrentHashMap<>();
    private final ClientBootstrap bootstrap;
    private final SocketOptions socketOptions;
    private final TlsContext tlsContext;
    private final int windowSize;

    public AwsCrtAsyncHttpClient(DefaultBuilder builder, AttributeMap serviceDefaultsMap) {
        this(builder.bootstrap, builder.socketOptions, builder.tlsContext, builder.windowSize);
    }

    public AwsCrtAsyncHttpClient(ClientBootstrap bootstrap, SocketOptions sockOpts, TlsContext tlsContext) {
        this(bootstrap, sockOpts, tlsContext, DEFAULT_STREAM_WINDOW_SIZE);
    }

    public AwsCrtAsyncHttpClient(ClientBootstrap bootstrap, SocketOptions sockOpts, TlsContext tlsContext, int windowSize) {
        Validate.notNull(bootstrap, "ClientBootstrap must not be null");
        Validate.notNull(sockOpts, "SocketOptions must not be null");
        Validate.notNull(tlsContext, "TlsContext must not be null");
        Validate.isPositive(windowSize, "windowSize must be > 0");

        this.bootstrap = bootstrap;
        this.socketOptions = sockOpts;
        this.tlsContext = tlsContext;
        this.windowSize = windowSize;
    }

    private static URI toUri(SdkHttpRequest sdkRequest) {
        Validate.notNull(sdkRequest, "SdkHttpRequest must not be null");
        return invokeSafely(() -> new URI(sdkRequest.protocol(), null, sdkRequest.host(),
                sdkRequest.port(), null, null, null));
    }

    public static Builder builder() {
        return new DefaultBuilder();
    }

    @Override
    public String clientName() {
        return CLIENT_NAME;
    }

    private HttpConnection createConnection(URI uri) {
        Validate.notNull(uri, "URI must not be null");
        // TODO: This is a Blocking call to establish a TCP and TLS connection
        return invokeSafely(() -> HttpConnection.createConnection(uri, bootstrap, socketOptions, tlsContext,
                                                                    windowSize).get());
    }

    private HttpConnection getOrCreateConnection(URI uri) {
        Validate.notNull(uri, "URI must not be null");
        HttpConnection connToReturn = connections.get(uri);

        if (connToReturn == null) {
            HttpConnection newConn = createConnection(uri);
            HttpConnection alreadyExistingConn = connections.putIfAbsent(uri, newConn);

            if (alreadyExistingConn == null) {
                connToReturn = newConn;
            } else {
                // Multiple threads trying to open connections to the same URI at once, close the newer one
                newConn.close();
                connToReturn = alreadyExistingConn;
            }
        }

        // TODO: Check if connection is shutdown/closed, and open a new connection if necessary

        return connToReturn;
    }

    private HttpRequest toCrtRequest(SdkHttpRequest sdkRequest) {
        Validate.notNull(sdkRequest, "SdkHttpRequest must not be null");

        String method = sdkRequest.method().name();
        String encodedPath = sdkRequest.encodedPath();

        List<HttpHeader> crtHeaderList = new ArrayList<>(sdkRequest.headers().size());

        // TODO: Host/Content-Length Header?
        // TODO: Are Headers Http Encoded?

        for (Map.Entry<String, List<String>> headerList: sdkRequest.headers().entrySet()) {
            for (String val: headerList.getValue()) {
                crtHeaderList.add(new HttpHeader(headerList.getKey(), val));
            }
        }

        HttpHeader[] crtHeaderArray = crtHeaderList.toArray(new HttpHeader[crtHeaderList.size()]);


        return new HttpRequest(method, encodedPath, crtHeaderArray);
    }

    @Override
    public CompletableFuture<Void> execute(AsyncExecuteRequest asyncRequest) {
        Validate.notNull(asyncRequest, "AsyncExecuteRequest must not be null");
        HttpConnection crtConn = getOrCreateConnection(toUri(asyncRequest.request()));
        HttpRequest crtRequest = toCrtRequest(asyncRequest.request());

        CompletableFuture<Void> requestFuture = new CompletableFuture<>();
        AwsCrtAsyncRequestResponseAdapter crtAdapter =
                new AwsCrtAsyncRequestResponseAdapter(requestFuture, asyncRequest, windowSize);

        invokeSafely(() -> crtConn.makeRequest(crtRequest, crtAdapter));

        return requestFuture;
    }

    @Override
    public void close() {
        for (HttpConnection conn : connections.values()) {
            // TODO: This shuts down and closes connections serially, can be made faster if shutdown in parallel.
            conn.close();
        }
    }

    /**
     * Builder that allows configuration of the AWS CRT HTTP implementation.
     */
    public interface Builder extends SdkAsyncHttpClient.Builder<AwsCrtAsyncHttpClient.Builder> {
        // TODO: Javadocs

        Builder bootstrap(ClientBootstrap boostrap);

        Builder socketOptions(SocketOptions socketOptions);

        Builder tlsContext(TlsContext tlsContext);

        Builder windowSize(int windowSize);
    }

    /**
     * Factory that allows more advanced configuration of the AWS CRT HTTP implementation. Use {@link #builder()} to
     * configure and construct an immutable instance of the factory.
     */
    private static final class DefaultBuilder implements Builder {
        private final AttributeMap.Builder standardOptions = AttributeMap.builder();

        private ClientBootstrap bootstrap;
        private SocketOptions socketOptions;
        private TlsContext tlsContext;
        private int windowSize = DEFAULT_STREAM_WINDOW_SIZE;

        private DefaultBuilder() {
        }

        @Override
        public SdkAsyncHttpClient build() {
            return new AwsCrtAsyncHttpClient(this, standardOptions.build()
                                                                  .merge(SdkHttpConfigurationOption.GLOBAL_HTTP_DEFAULTS));
        }

        @Override
        public SdkAsyncHttpClient buildWithDefaults(AttributeMap serviceDefaults) {
            return new AwsCrtAsyncHttpClient(this, standardOptions.build()
                                                           .merge(serviceDefaults)
                                                           .merge(SdkHttpConfigurationOption.GLOBAL_HTTP_DEFAULTS));
        }

        public Builder bootstrap(ClientBootstrap bootstrap) {
            Validate.notNull(bootstrap, "bootstrap");
            this.bootstrap = bootstrap;
            return this;
        }

        @Override
        public Builder socketOptions(SocketOptions socketOptions) {
            Validate.notNull(socketOptions, "socketOptions");
            this.socketOptions = socketOptions;
            return this;
        }

        @Override
        public Builder tlsContext(TlsContext tlsContext) {
            Validate.notNull(tlsContext, "tlsContext");
            this.tlsContext = tlsContext;
            return this;
        }

        @Override
        public Builder windowSize(int windowSize) {
            Validate.isPositive(windowSize, "windowSize");
            this.windowSize = windowSize;
            return this;
        }
    }
}
