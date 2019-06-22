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

import static software.amazon.awssdk.crt.utils.ByteBufferUtils.transferData;

import java.nio.ByteBuffer;
import java.util.Queue;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import org.reactivestreams.Subscriber;
import org.reactivestreams.Subscription;

import software.amazon.awssdk.annotations.SdkInternalApi;
import software.amazon.awssdk.http.async.SdkHttpContentPublisher;
import software.amazon.awssdk.utils.Logger;
import software.amazon.awssdk.utils.Validate;

/**
 * Implements the Subscriber<ByteBuffer> API to make it easier to use from AwsCrtAsyncHttpStreamAdapter.sendRequestBody()
 */
@SdkInternalApi
public class AwsCrtRequestBodySubscriber implements Subscriber<ByteBuffer> {
    private static final Logger log = Logger.loggerFor(AwsCrtRequestBodySubscriber.class);

    private final int windowSize;
    private final Queue<ByteBuffer> queuedBuffers = new ConcurrentLinkedQueue<>();
    private final AtomicInteger queuedBytes = new AtomicInteger(0);
    private final AtomicBoolean isComplete = new AtomicBoolean(false);
    private final AtomicReference<Throwable> error = new AtomicReference<>(null);

    private AtomicReference<Subscription> subscriptionRef = new AtomicReference<>(null);

    /**
     *
     * @param windowSize The number bytes to be queued before we stop proactively queuing data
     */
    AwsCrtRequestBodySubscriber(SdkHttpContentPublisher reqBodyPublisher, int windowSize) {
        Validate.notNull(reqBodyPublisher, "SdkHttpContentPublisher is null");
        Validate.isPositive(windowSize, "windowSize is <= 0");

        this.windowSize = windowSize;
        reqBodyPublisher.subscribe(this);
    }

    protected void requestDataIfNecessary() {
        Subscription subscription = subscriptionRef.get();
        if (subscription == null) {
            log.error(() -> "Subscription is null");
            return;
        }
        if (queuedBytes.get() < windowSize) {
            log.trace(() -> "Requesting more data from subscription");
            subscription.request(1);
        }
    }

    @Override
    public void onSubscribe(Subscription s) {
        boolean wasFirstSubscription = subscriptionRef.compareAndSet(null, s);

        if (!wasFirstSubscription) {
            log.error(() -> "onSubscribe() Callback called twice");
            throw new IllegalStateException("Was not first subscriptionRef!");
        }

        requestDataIfNecessary();
    }

    @Override
    public void onNext(ByteBuffer byteBuffer) {
        log.trace(() -> "onNext() byteBuffer size: " + byteBuffer.remaining());
        queuedBuffers.add(byteBuffer);
        queuedBytes.addAndGet(byteBuffer.remaining());
        requestDataIfNecessary();
    }

    @Override
    public void onError(Throwable t) {
        log.info(() -> "onError() received an error: " + t.getMessage());
        error.compareAndSet(null, t);
    }

    @Override
    public void onComplete() {
        log.info(() -> "onComplete() called");
        isComplete.set(true);
    }



    /**
     * Transfers any queued data from the Request Body subscriptionRef to the output buffer
     * @param out The output ByteBuffer
     * @return true if Request Body is completely transferred, false otherwise
     */
    public synchronized boolean transferRequestBody(ByteBuffer out) {
        if (error.get() != null) {
            throw new RuntimeException(error.get());
        }

        while (out.remaining() > 0 && queuedBuffers.size() > 0) {
            ByteBuffer nextBuffer = queuedBuffers.peek();
            int amtTransferred = transferData(nextBuffer, out);
            queuedBytes.addAndGet(-amtTransferred);

            if (nextBuffer.remaining() == 0) {
                queuedBuffers.remove();
            }
        }


        boolean endOfStream = (queuedBuffers.size() == 0) && isComplete.get();

        if (!endOfStream) {
            requestDataIfNecessary();
        } else {
            log.trace(() -> "Subscription completed");
        }

        return endOfStream;
    }
}
