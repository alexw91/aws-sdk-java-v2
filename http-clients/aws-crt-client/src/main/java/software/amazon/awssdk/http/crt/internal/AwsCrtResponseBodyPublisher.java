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

import java.nio.ByteBuffer;
import java.util.Queue;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.LongUnaryOperator;
import org.reactivestreams.Publisher;
import org.reactivestreams.Subscriber;

import software.amazon.awssdk.annotations.SdkInternalApi;
import software.amazon.awssdk.crt.http.HttpStream;
import software.amazon.awssdk.utils.Logger;
import software.amazon.awssdk.utils.Validate;

/**
 * Adapts an Response Body stream from CrtHttpStreamHandler to a Publisher<ByteBuffer>
 */
@SdkInternalApi
public class AwsCrtResponseBodyPublisher implements Publisher<ByteBuffer> {
    private static final LongUnaryOperator DECREMENT_IF_GREATER_THAN_ZERO = x -> ((x > 0) ? (x - 1) : (x));
    private static final Logger log = Logger.loggerFor(AwsCrtResponseBodyPublisher.class);

    private final AtomicLong outstandingRequests = new AtomicLong(0);
    private final HttpStream stream;
    private final int windowSize;
    private final AtomicBoolean isCancelled = new AtomicBoolean(false);
    private final AtomicBoolean isComplete = new AtomicBoolean(false);
    private final AtomicInteger queuedBytes = new AtomicInteger(0);
    private final AtomicReference<Subscriber<? super ByteBuffer>> subscriber = new AtomicReference<>(null);
    private final Queue<ByteBuffer> queuedBuffers = new ConcurrentLinkedQueue<>();
    private final AtomicReference<Throwable> error = new AtomicReference<>(null);

    /**
     * Adapts a streaming AWS CRT Http Response Body to a Publisher<ByteBuffer>
     * @param stream The AWS CRT Http Stream for this Response
     * @param windowSize The max allowed bytes to be queued. The sum of the sizes of all queued ByteBuffers should
     *                   never exceed this value.
     */
    public AwsCrtResponseBodyPublisher(HttpStream stream, int windowSize) {
        Validate.notNull(stream, "Stream must not be null");
        Validate.isPositive(windowSize, "windowSize must be > 0");
        this.stream = stream;
        this.windowSize = windowSize;
    }

    @Override
    public void subscribe(Subscriber<? super ByteBuffer> application) {
        Validate.notNull(application, "Subscriber must not be null");

        boolean wasFirstSubscriber = subscriber.compareAndSet(null, application);

        if (!wasFirstSubscriber) {
            application.onError(new RuntimeException("Only one subscriber allowed"));
            return;
        }

        subscriber.get().onSubscribe(new AwsCrtResponseBodySubscription(this));
    }

    /**
     * Adds a Buffer to the Queue
     *
     * @param buffer
     */
    protected void queueBuffer(ByteBuffer buffer) {
        Validate.notNull(buffer, "ByteBuffer must not be null");

        if (isCancelled.get()) {
            // Immediately open HttpStream's IO window so it doesn't see any IO Back-pressure.
            // AFAIK there's no way to abort an in-progress HttpStream, only free it's memory by calling close()
            stream.incrementWindow(buffer.remaining());
            return;
        }

        queuedBuffers.add(buffer);
        int totalBytesQueued = queuedBytes.addAndGet(buffer.remaining());

        if (totalBytesQueued > windowSize) {
            throw new IllegalStateException("Queued more than Window Size: queued=" + totalBytesQueued
                                            + ", window=" + windowSize);
        }
    }

    protected void request(long n) {
        outstandingRequests.addAndGet(n);
    }

    protected void setError(Throwable t) {
        log.error(() -> "Error processing Response Body", t);
        error.compareAndSet(null, t);
    }

    protected void setCancelled() {
        isCancelled.set(true);
    }

    protected void setComplete() {
        isComplete.set(true);
    }

    /**
     * This method MUST be synchronized since it can be called simultaneously from both the Native EventLoop Thread and
     * the User Thread. If this method wasn't synchronized, it'd be possible for each thread to dequeue a buffer by
     * calling queuedBuffers.poll(), but then have the 2nd thread call subscriber.onNext(buffer) first, resulting in the
     * subscriber seeing out-of-order data. To avoid this race condition, this method must be synchronized.
     */
    protected synchronized void publishToSubscribers() {
        if (subscriber.get() == null) {
            return;
        }

        if (error.get() != null) {
            subscriber.get().onError(error.get());
            return;
        }

        int totalAmountTransferred = 0;

        // Push data to Subscribers
        while (outstandingRequests.get() > 0 && queuedBuffers.size() > 0) {
            ByteBuffer buffer = queuedBuffers.poll();
            outstandingRequests.getAndUpdate(DECREMENT_IF_GREATER_THAN_ZERO);
            int amount = buffer.remaining();
            subscriber.get().onNext(buffer);
            totalAmountTransferred += amount;
        }

        if (totalAmountTransferred > 0) {
            queuedBytes.addAndGet(-totalAmountTransferred);
            // Open HttpStream's IO window so HttpStream can keep track of IO back-pressure
            stream.incrementWindow(totalAmountTransferred);
        }

        // Check if Complete
        if (queuedBuffers.size() == 0 && isComplete.get()) {
            subscriber.get().onComplete();
        }
    }

}
