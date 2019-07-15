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
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;

import software.amazon.awssdk.annotations.SdkPublicApi;
import software.amazon.awssdk.crt.http.HttpConnection;
import software.amazon.awssdk.crt.http.HttpHeader;
import software.amazon.awssdk.crt.http.HttpRequest;
import software.amazon.awssdk.crt.io.ClientBootstrap;
import software.amazon.awssdk.crt.io.SocketOptions;
import software.amazon.awssdk.crt.io.TlsContext;
import software.amazon.awssdk.http.SdkHttpClient;
import software.amazon.awssdk.http.SdkHttpConfigurationOption;
import software.amazon.awssdk.http.SdkHttpRequest;
import software.amazon.awssdk.http.async.AsyncExecuteRequest;
import software.amazon.awssdk.http.async.SdkAsyncHttpClient;
import software.amazon.awssdk.http.crt.internal.AwsCrtAsyncHttpStreamAdapter;
import software.amazon.awssdk.utils.AttributeMap;
import software.amazon.awssdk.utils.Logger;
import software.amazon.awssdk.utils.Validate;
import software.amazon.awssdk.utils.http.SdkHttpUtils;

/**
 * An implementation of {@link SdkHttpClient} that uses the AWS Common Runtime (CRT) Http Client to communicate with
 * Http Web Services. This client is asynchronous and uses non-blocking IO.
 *
 * <p>This can be created via {@link #builder()}</p>
 */
@SdkPublicApi
public class AwsCrtAsyncHttpClient implements SdkAsyncHttpClient {
    private static final Logger log = Logger.loggerFor(AwsCrtAsyncHttpClient.class);
    private static final String HOST_HEADER = "Host";
    private static final String CONTENT_LENGTH = "Content-Length";
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

    private static boolean isNullOrEmpty(List<String> list) {
        if (list == null || list.size() == 0) {
            return true;
        }
        return false;
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

        // If connection was shutdown by peer, open a new connection
        if (connToReturn.getShutdownFuture().isDone()) {
            connections.remove(uri, connToReturn);
            connToReturn.close();
            return getOrCreateConnection(uri);
        }

        return connToReturn;
    }

    private List<HttpHeader> createHttpHeaderList(URI uri, AsyncExecuteRequest asyncRequest) {
        SdkHttpRequest sdkRequest = asyncRequest.request();
        List<HttpHeader> crtHeaderList = new ArrayList<>(sdkRequest.headers().size() + 2);

        // Set Host Header if needed
        if (isNullOrEmpty(sdkRequest.headers().get(HOST_HEADER))) {
            crtHeaderList.add(new HttpHeader(HOST_HEADER, uri.getHost()));
        }

        // Set Content-Length if needed
        Optional<Long> contentLength = asyncRequest.requestContentPublisher().contentLength();
        if (isNullOrEmpty(sdkRequest.headers().get(CONTENT_LENGTH)) && contentLength.isPresent()) {
            crtHeaderList.add(new HttpHeader(CONTENT_LENGTH, Long.toString(contentLength.get())));
        }

        // Add the rest of the Headers
        for (Map.Entry<String, List<String>> headerList: sdkRequest.headers().entrySet()) {
            for (String val: headerList.getValue()) {
                HttpHeader h = new HttpHeader(headerList.getKey(), val);
                crtHeaderList.add(h);
            }
        }

        return crtHeaderList;
    }

    private HttpHeader[] asArray(List<HttpHeader> crtHeaderList) {
        return crtHeaderList.toArray(new HttpHeader[crtHeaderList.size()]);
    }

    private HttpRequest toCrtRequest(URI uri, AsyncExecuteRequest asyncRequest) {
        SdkHttpRequest sdkRequest = asyncRequest.request();
        Validate.notNull(uri, "URI must not be null");
        Validate.notNull(sdkRequest, "SdkHttpRequest must not be null");

        String method = sdkRequest.method().name();
        String encodedPath = sdkRequest.encodedPath();
        String encodedQueryString = SdkHttpUtils.encodeAndFlattenQueryParameters(sdkRequest.rawQueryParameters())
                .map(value -> "?" + value)
                .orElse("");

        HttpHeader[] crtHeaderArray = asArray(createHttpHeaderList(uri, asyncRequest));

        return new HttpRequest(method, encodedPath + encodedQueryString, crtHeaderArray);
    }

    @Override
    public CompletableFuture<Void> execute(AsyncExecuteRequest asyncRequest) {
        Validate.notNull(asyncRequest, "AsyncExecuteRequest must not be null");
        Validate.notNull(asyncRequest.request(), "SdkHttpRequest must not be null");
        Validate.notNull(asyncRequest.requestContentPublisher(), "RequestContentPublisher must not be null");
        Validate.notNull(asyncRequest.responseHandler(), "ResponseHandler must not be null");

        URI uri = toUri(asyncRequest.request());
        HttpConnection crtConn = getOrCreateConnection(uri);
        HttpRequest crtRequest = toCrtRequest(uri, asyncRequest);

        CompletableFuture<Void> requestFuture = new CompletableFuture<>();
        AwsCrtAsyncHttpStreamAdapter crtAdapter =
                new AwsCrtAsyncHttpStreamAdapter(requestFuture, asyncRequest, windowSize);

        invokeSafely(() -> crtConn.makeRequest(crtRequest, crtAdapter));

        return requestFuture;
    }

    @Override
    public void close() {
        for (HttpConnection conn : connections.values()) {
            conn.close();
        }
    }

    /**
     * Builder that allows configuration of the AWS CRT HTTP implementation.
     */
    public interface Builder extends SdkAsyncHttpClient.Builder<AwsCrtAsyncHttpClient.Builder> {

        /**
         * The AWS CRT Bootstrap Instance to use for this Client
         * @param boostrap The AWS Common Runtime Bootstrap
         * @return the builder of the method chaining.
         */
        Builder bootstrap(ClientBootstrap boostrap);

        /**
         * The AWS CRT SocketOptions to use for this Client.
         * @param socketOptions The AWS Common Runtime SocketOptions
         * @return the builder of the method chaining.
         */
        Builder socketOptions(SocketOptions socketOptions);

        /**
         * The AWS CRT TlsContext to use for this Client
         * @param tlsContext The AWS Common Runtime TlsContext
         * @return the builder of the method chaining.
         */
        Builder tlsContext(TlsContext tlsContext);

        /**
         * The AWS CRT WindowSize to use for this HttpClient
         * @param windowSize The AWS Common Runtime WindowSize
         * @return the builder of the method chaining.
         */
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
