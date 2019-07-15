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
    private static final Logger log = Logger.loggerFor(AwsCrtResponseBodyPublisher.class);
    private static final LongUnaryOperator DECREMENT_IF_GREATER_THAN_ZERO = x -> ((x > 0) ? (x - 1) : (x));

    private final AtomicLong outstandingRequests = new AtomicLong(0);
    private final HttpStream stream;
    private final int windowSize;
    private final AtomicBoolean isCancelled = new AtomicBoolean(false);
    private final AtomicBoolean isSubscriptionComplete = new AtomicBoolean(false);
    private final AtomicBoolean queueComplete = new AtomicBoolean(false);
    private final AtomicInteger mutualRecursionDepth = new AtomicInteger(0);
    private final AtomicInteger queuedBytes = new AtomicInteger(0);
    private final AtomicReference<Subscriber<? super ByteBuffer>> subscriberRef = new AtomicReference<>(null);
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

        boolean wasFirstSubscriber = subscriberRef.compareAndSet(null, application);

        if (!wasFirstSubscriber) {
            log.error(() -> "Only one subscriber allowed");
            application.onError(new IllegalStateException("Only one subscriber allowed"));
            return;
        }

        application.onSubscribe(new AwsCrtResponseBodySubscription(this));
    }

    /**
     * Adds a Buffer to the Queue to be published to any Subscribers
     * @param buffer The Buffer to be queued.
     */
    public void queueBuffer(ByteBuffer buffer) {
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

    /**
     * Function called by subscribers to request more buffers.
     * @param n The number of buffers requested.
     */
    protected void request(long n) {
        Validate.inclusiveBetween(1, Long.MAX_VALUE, n, "request");

        // Check for overflow of outstanding Requests, and clamp to LONG_MAX.
        long remaining;
        if (n > (Long.MAX_VALUE - outstandingRequests.get())) {
            outstandingRequests.set(Long.MAX_VALUE);
            remaining = Long.MAX_VALUE;
        } else {
            remaining = outstandingRequests.addAndGet(n);
        }

        log.trace(() -> "Subscriber Requested more Data. Outstanding Requests: " + remaining);
    }

    public void setError(Throwable t) {
        log.error(() -> "Error processing Response Body", t);
        error.compareAndSet(null, t);
    }

    protected void setCancelled() {
        isCancelled.set(true);
        subscriberRef.set(null);
    }

    public void setQueueComplete() {
        queueComplete.set(true);
        log.trace(() -> "Response Body Publisher queue marked as completed.");
    }

    protected void completeSubscription() {
        boolean wasComplete = isSubscriptionComplete.getAndSet(true);

        if (wasComplete) {
            return;
        }

        Subscriber s = subscriberRef.getAndSet(null);

        if (s == null) {
            return;
        }

        Throwable throwable = error.get();

        if (throwable != null) {
            s.onError(throwable);
        } else {
            s.onComplete();
        }
    }

    /**
     * This method MUST be synchronized since it can be called simultaneously from both the Native EventLoop Thread and
     * the User Thread. If this method wasn't synchronized, it'd be possible for each thread to dequeue a buffer by
     * calling queuedBuffers.poll(), but then have the 2nd thread call subscriber.onNext(buffer) first, resulting in the
     * subscriber seeing out-of-order data. To avoid this race condition, this method must be synchronized.
     */
    protected synchronized void publishToSubscribers() {
        Subscriber subscriber = subscriberRef.get();
        if (subscriber == null) {
            log.warn(() -> "No Subscribers to publish to");
            return;
        }

        if (error.get() != null) {
            completeSubscription();
            return;
        }

        if (mutualRecursionDepth.get() > 0) {
            /**
             * If our depth is > 0, then we already made a call to publishToSubscribers() further up the stack that
             * will continue publishing to subscribers, and this call should return without completing work to avoid
             * infinite recursive loop between: "subscription.request() -> subscriber.onNext() -> subscription.request()"
             */
            return;
        }

        int totalAmountTransferred = 0;

        while (outstandingRequests.get() > 0 && queuedBuffers.size() > 0) {
            ByteBuffer buffer = queuedBuffers.poll();
            outstandingRequests.getAndUpdate(DECREMENT_IF_GREATER_THAN_ZERO);
            int amount = buffer.remaining();
            publishWithoutMutualRecursion(subscriber, buffer);
            totalAmountTransferred += amount;
        }

        if (totalAmountTransferred > 0) {
            queuedBytes.addAndGet(-totalAmountTransferred);
            // Open HttpStream's IO window so HttpStream can keep track of IO back-pressure
            stream.incrementWindow(totalAmountTransferred);
        }

        // Check if Complete
        if (queueComplete.get() && queuedBuffers.size() == 0) {
            completeSubscription();
        }
    }

    /**
     * This method is used to avoid a StackOverflow due to the potential infinite loop between
     * "subscription.request() -> subscriber.onNext() -> subscription.request()" calls. We only call subscriber.onNext()
     * if the recursion depth is zero, otherwise we return up to the stack frame with depth zero and continue publishing
     * from there.
     * @param subscriber The Subscriber to publish to.
     * @param buffer The buffer to publish to the subscriber.
     */
    private synchronized void publishWithoutMutualRecursion(Subscriber<ByteBuffer> subscriber, ByteBuffer buffer) {
        try {
            /**
             * Need to keep track of recursion depth between .onNext() -> .request() calls
             */
            int depth = mutualRecursionDepth.getAndIncrement();
            if (depth == 0) {
                subscriber.onNext(buffer);
            }
        } finally {
            mutualRecursionDepth.decrementAndGet();
        }
    }

}