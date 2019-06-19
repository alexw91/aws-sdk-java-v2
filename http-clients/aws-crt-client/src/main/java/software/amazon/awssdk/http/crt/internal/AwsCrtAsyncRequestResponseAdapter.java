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

package software.amazon.awssdk.http.crt.internal;

import static software.amazon.awssdk.http.crt.internal.ByteBufferUtils.deepCopy;

import java.nio.ByteBuffer;
import java.util.concurrent.CompletableFuture;
import software.amazon.awssdk.annotations.SdkInternalApi;
import software.amazon.awssdk.crt.CRT;
import software.amazon.awssdk.crt.http.CrtHttpStreamHandler;
import software.amazon.awssdk.crt.http.HttpException;
import software.amazon.awssdk.crt.http.HttpHeader;
import software.amazon.awssdk.crt.http.HttpStream;
import software.amazon.awssdk.http.SdkHttpResponse;
import software.amazon.awssdk.http.async.AsyncExecuteRequest;
import software.amazon.awssdk.utils.Logger;

/**
 * Converts the CrtHttpStreamHandler Callbacks to call SDK AsyncExecuteRequest methods
 */
@SdkInternalApi
public class AwsCrtAsyncRequestResponseAdapter implements CrtHttpStreamHandler {
    private static final Logger log = Logger.loggerFor(AwsCrtAsyncRequestResponseAdapter.class);
    private final AsyncExecuteRequest sdkRequest;
    private final CompletableFuture<Void> reqComplete;
    private final SdkHttpResponse.Builder respBuilder = SdkHttpResponse.builder();
    private final int windowSize;
    private final AwsCrtRequestBodySubscriber requestBodySubscriber;
    private AwsCrtResponseBodyPublisher respBodyPublisher = null;

    public AwsCrtAsyncRequestResponseAdapter(CompletableFuture<Void> reqComplete, AsyncExecuteRequest sdkRequest,
                                             int windowSize) {
        this.sdkRequest = sdkRequest;
        this.reqComplete = reqComplete;
        this.windowSize = windowSize;
        this.requestBodySubscriber = new AwsCrtRequestBodySubscriber(sdkRequest.requestContentPublisher(), windowSize);
    }

    @Override
    public void onResponseHeaders(HttpStream stream, int responseStatusCode, HttpHeader[] nextHeaders) {
        respBuilder.statusCode(responseStatusCode);

        log.info(() -> "Response Status: " + responseStatusCode);
        for (HttpHeader h : nextHeaders) {
            log.info(() -> "Response Header: " + h.toString());
            respBuilder.appendHeader(h.getName(), h.getValue());
        }
    }

    @Override
    public void onResponseHeadersDone(HttpStream stream, boolean hasBody) {
        respBuilder.statusCode(stream.getResponseStatusCode());
        sdkRequest.responseHandler().onHeaders(respBuilder.build());
        respBodyPublisher = new AwsCrtResponseBodyPublisher(stream, windowSize);

        log.info(() -> "Response Headers Done. hasBody:" + hasBody);

        if (!hasBody) {
            respBodyPublisher.setComplete();
        }

        sdkRequest.responseHandler().onStream(respBodyPublisher);
    }

    @Override
    public int onResponseBody(HttpStream stream, ByteBuffer bodyBytesIn) {
        if (respBodyPublisher == null) {
            log.error(() -> "Publisher is null, onResponseHeadersDone() was never called");
            throw new IllegalStateException("Publisher is null, onResponseHeadersDone() was never called");
        }

        log.info(() -> "Response Body chunk:" + bodyBytesIn.remaining());

        ByteBuffer copy = deepCopy(bodyBytesIn);
        respBodyPublisher.queueBuffer(copy);
        respBodyPublisher.publishToSubscribers();

        return 0;
    }

    @Override
    public void onResponseComplete(HttpStream stream, int errorCode) {
        if (errorCode == CRT.AWS_CRT_SUCCESS) {
            log.info(() -> "Response Completed Successfully");
            respBodyPublisher.setComplete();
            respBodyPublisher.publishToSubscribers();
            reqComplete.complete(null);
        } else {
            HttpException error = new HttpException(errorCode);
            log.error(() -> "Response Encountered an Error.", error);

            // Invoke Error Callback on SdkAsyncHttpResponseHandler
            sdkRequest.responseHandler().onError(error);

            // Invoke Error Callback on any Subscriber's of the Response Body
            if (respBodyPublisher != null) {
                respBodyPublisher.setError(error);
                respBodyPublisher.publishToSubscribers();
            }

            reqComplete.completeExceptionally(error);
        }

        stream.close();
    }

    @Override
    public boolean sendRequestBody(HttpStream stream, ByteBuffer bodyBytesOut) {
        log.info(() -> "Request Body chunk:" + bodyBytesOut.remaining());
        return requestBodySubscriber.transferRequestBody(bodyBytesOut);
    }
}
