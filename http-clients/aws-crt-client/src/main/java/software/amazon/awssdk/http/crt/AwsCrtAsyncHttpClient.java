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
import software.amazon.awssdk.http.SdkHttpRequest;
import software.amazon.awssdk.http.async.AsyncExecuteRequest;
import software.amazon.awssdk.http.async.SdkAsyncHttpClient;
import software.amazon.awssdk.http.crt.internal.AwsCrtAsyncRequestResponseAdapter;
import software.amazon.awssdk.utils.AttributeMap;

@SdkPublicApi
public class AwsCrtAsyncHttpClient implements SdkAsyncHttpClient {
    private static final String CLIENT_NAME = "AwsCommonRuntime";
    private static final int DEFAULT_STREAM_WINDOW_SIZE = 4 * 1024 * 1024; // Queue up to 4 MB of Http Body Bytes
    private final Map<URI, HttpConnection> connections = new ConcurrentHashMap<>();
    private final ClientBootstrap bootstrap;
    private final SocketOptions socketOptions;
    private final TlsContext tlsContext;
    private final int windowSize;



    public AwsCrtAsyncHttpClient(ClientBootstrap bootstrap, SocketOptions sockOpts, TlsContext tlsContext) {
        this(bootstrap, sockOpts, tlsContext, DEFAULT_STREAM_WINDOW_SIZE);
    }

    public AwsCrtAsyncHttpClient(ClientBootstrap bootstrap, SocketOptions sockOpts, TlsContext tlsContext, int windowSize) {
        this.bootstrap = bootstrap;
        this.socketOptions = sockOpts;
        this.tlsContext = tlsContext;
        this.windowSize = windowSize;
    }

    private static URI toUri(SdkHttpRequest sdkRequest) {
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
        // TODO: This is a Blocking call to establish a TCP and TLS connection
        return invokeSafely(() -> HttpConnection.createConnection(uri, bootstrap, socketOptions, tlsContext,
                                                                    windowSize).get());
    }

    private HttpConnection getOrCreateConnection(URI uri) {
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
        String method = sdkRequest.method().name();
        String encodedPath = sdkRequest.encodedPath();
        HttpHeader[] headers = new HttpHeader[sdkRequest.headers().size()];

        // TODO: Host/Content-Length Header?
        int i = 0;
        for (Map.Entry<String, List<String>> e : sdkRequest.headers().entrySet()) {
            // TODO: Is this String.join() correct? https://stackoverflow.com/a/4371395
            // TODO: Are Headers Http Encoded?
            headers[i] = new HttpHeader(e.getKey(), String.join(", ", e.getValue()));
            i++;
        }

        return new HttpRequest(method, encodedPath, headers);
    }

    @Override
    public CompletableFuture<Void> execute(AsyncExecuteRequest asyncRequest) {
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
     * Builder that allows configuration of the Netty NIO HTTP implementation. Use {@link #builder()} to configure and construct
     * a Netty HTTP client.
     */
    public interface Builder extends SdkAsyncHttpClient.Builder<AwsCrtAsyncHttpClient.Builder> {
        // TODO: Add Builder params
    }

    /**
     * Factory that allows more advanced configuration of the Netty NIO HTTP implementation. Use {@link #builder()} to
     * configure and construct an immutable instance of the factory.
     */
    private static final class DefaultBuilder implements Builder {

        private DefaultBuilder() {
        }

        @Override
        public SdkAsyncHttpClient buildWithDefaults(AttributeMap serviceDefaults) {
            // TODO
            return null;
        }
    }
}
