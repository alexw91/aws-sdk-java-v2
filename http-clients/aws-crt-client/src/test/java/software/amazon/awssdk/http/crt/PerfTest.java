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

import static java.util.stream.Collectors.toList;
import static org.apache.commons.codec.digest.DigestUtils.sha256Hex;
import static org.assertj.core.api.Assertions.assertThat;

import io.netty.handler.ssl.SslProvider;
import java.nio.file.Paths;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Semaphore;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Consumer;
import java.util.function.Supplier;
import java.util.stream.IntStream;

import org.apache.log4j.BasicConfigurator;
import org.junit.BeforeClass;
import org.junit.Test;
import software.amazon.awssdk.core.async.AsyncResponseTransformer;
import software.amazon.awssdk.crt.io.ClientBootstrap;
import software.amazon.awssdk.crt.io.SocketOptions;
import software.amazon.awssdk.crt.io.TlsContext;
import software.amazon.awssdk.http.apache.ApacheHttpClient;
import software.amazon.awssdk.http.async.AsyncExecuteRequest;
import software.amazon.awssdk.http.async.SdkAsyncHttpClient;
import software.amazon.awssdk.http.nio.netty.NettyNioAsyncHttpClient;
import software.amazon.awssdk.http.urlconnection.UrlConnectionHttpClient;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.dynamodb.DynamoDbAsyncClient;
import software.amazon.awssdk.services.dynamodb.DynamoDbClient;
import software.amazon.awssdk.services.dynamodb.model.AttributeValue;
import software.amazon.awssdk.services.dynamodb.model.GetItemRequest;
import software.amazon.awssdk.services.dynamodb.model.KeyType;
import software.amazon.awssdk.services.dynamodb.model.ResourceInUseException;
import software.amazon.awssdk.services.dynamodb.model.ScalarAttributeType;
import software.amazon.awssdk.services.dynamodb.model.TableStatus;
import software.amazon.awssdk.services.s3.S3AsyncClient;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.BucketAlreadyExistsException;
import software.amazon.awssdk.services.s3.model.BucketAlreadyOwnedByYouException;
import software.amazon.awssdk.services.s3.model.GetObjectRequest;
import software.amazon.awssdk.testutils.Waiter;
import software.amazon.awssdk.utils.AttributeMap;
import software.amazon.awssdk.utils.ImmutableMap;
import software.amazon.awssdk.utils.IoUtils;


public class PerfTest {
    private static final String DDB_TABLE = "berks";
    private static Region REGION = Region.US_EAST_1;
    private static final String S3_BUCKET = "aws-crt-test-stuff";
    private static final String S3_FILENAME = "random_32_byte.data";


    private static final int CONCURRENT_REQUESTS = 2;
    private static final int NUM_RUNS = 3;
    private static final Duration RUN_DURATION = Duration.ofSeconds(1);

    private static final S3AsyncClient S3_CRT =
            S3AsyncClient.builder()
                    .region(REGION)
                    .httpClientBuilder(
                            AwsCrtAsyncHttpClient.builder()
                                    .bootstrap(new ClientBootstrap(Runtime.getRuntime().availableProcessors()))
                                    .socketOptions(new SocketOptions())
                                    .tlsContext(new TlsContext()))
                    .build();

    private static final DynamoDbAsyncClient CRT =
            DynamoDbAsyncClient.builder()
                    .httpClientBuilder(
                            AwsCrtAsyncHttpClient.builder()
                                    .bootstrap(new ClientBootstrap(Runtime.getRuntime().availableProcessors()))
                                    .socketOptions(new SocketOptions())
                                    .tlsContext(new TlsContext()))
                    .build();

    private static final S3AsyncClient S3_NETTY =
            S3AsyncClient.builder()
                    .region(REGION)
                    .httpClientBuilder(NettyNioAsyncHttpClient.builder()
                            .maxConcurrency(CONCURRENT_REQUESTS)
                            .sslProvider(SslProvider.OPENSSL))
                    .build();

    private static final DynamoDbAsyncClient NETTY =
            DynamoDbAsyncClient.builder()
                    .httpClientBuilder(NettyNioAsyncHttpClient.builder()
                            .maxConcurrency(CONCURRENT_REQUESTS)
                            .sslProvider(SslProvider.OPENSSL))
                    .build();

    private static final S3Client S3_APACHE =
            S3Client.builder()
                    .httpClientBuilder(ApacheHttpClient.builder()
                            .maxConnections(CONCURRENT_REQUESTS))
                    .build();

    private static final DynamoDbClient APACHE =
            DynamoDbClient.builder()
                    .httpClientBuilder(ApacheHttpClient.builder()
                            .maxConnections(CONCURRENT_REQUESTS))
                    .build();

    private static final DynamoDbClient URL_CONNECTION =
            DynamoDbClient.builder()
                    .httpClientBuilder(UrlConnectionHttpClient.builder())
                    .build();

    @BeforeClass
    public static void setup() {
        BasicConfigurator.configure();
        try {
            APACHE.createTable(r -> r.tableName(DDB_TABLE)
                    .keySchema(k -> k.attributeName("id").keyType(KeyType.HASH))
                    .attributeDefinitions(a -> a.attributeName("id").attributeType(ScalarAttributeType.S))
                    .provisionedThroughput(t -> t.readCapacityUnits(5000L)
                            .writeCapacityUnits(5L)));
        } catch (ResourceInUseException e) {
            // Table already exists. Awesome.
        }

        try {
            S3_APACHE.createBucket(r -> r.bucket(S3_BUCKET));
        } catch (BucketAlreadyExistsException | BucketAlreadyOwnedByYouException e) {
            // Bucket already exists. Awesome.
        }

        System.out.println("Waiting for table to be active...");

        Waiter.run(() -> APACHE.describeTable(r -> r.tableName(DDB_TABLE)))
                .until(r -> r.table().tableStatus().equals(TableStatus.ACTIVE))
                .orFail();

        APACHE.putItem(r -> r.tableName(DDB_TABLE).item(ImmutableMap.of("id", AttributeValue.builder().s("foo").build())));
        //S3_APACHE.putObject(r -> r.bucket(S3_BUCKET).key("foo"), Paths.get("/tmp/out"));
    }

    //    @AfterClass
    //    public static void cleanup() {
    //        boolean deleted =
    //                Waiter.run(() -> APACHE.deleteTable(r -> r.tableName(DDB_TABLE)))
    //                      .ignoringException(DynamoDbException.class)
    //                      .orReturnFalse();
    //
    //        if (!deleted) {
    //            System.err.println("Table could not be cleaned up.");
    //        }
    //    }


    private void sanityCheck(S3AsyncClient client) throws Exception {
        GetObjectRequest s3Request = GetObjectRequest.builder()
                .bucket("aws-crt-test-stuff")
                .key("http_test_doc.txt")
                .build();

        byte[] responseBody = client.getObject(s3Request, AsyncResponseTransformer.toBytes()).get(120, TimeUnit.SECONDS).asByteArray();
        assertThat(sha256Hex(responseBody).toUpperCase()).isEqualTo("C7FDB5314B9742467B16BD5EA2F8012190B5E2C44A005F7984F89AAB58219534");

        System.out.println("Sanity Check Passed");
    }


    @Test
    public void comparePerformance() throws Exception {
        System.out.println("Parameters: (ConcurrentRequests=" + CONCURRENT_REQUESTS + ", RunDuration=" + RUN_DURATION.getSeconds() + "s)");

//        sanityCheck(S3_CRT);

        asyncPerformanceTest("CRT", () -> invoke(S3_CRT));
//        asyncPerformanceTest("Netty", () -> invoke(S3_NETTY));
        //        syncPerformanceTest("Apache", () -> invoke(APACHE));
        //        syncPerformanceTest("URL Connection", () -> invoke(URL_CONNECTION));
    }

    private void syncPerformanceTest(String testCase, Runnable runnable) throws InterruptedException {
        ExecutorService executorService = Executors.newFixedThreadPool(CONCURRENT_REQUESTS);
        try {
            asyncPerformanceTest(testCase, () -> {
                CompletableFuture<Long> result = new CompletableFuture<>();
                executorService.submit(() -> {
                    try {
                        long start = System.currentTimeMillis();
                        runnable.run();
                        result.complete(System.currentTimeMillis() - start);
                    } catch (Exception e) {
                        result.completeExceptionally(e);
                    }
                });
                return result;
            });
        } finally {
            executorService.shutdown();
        }
    }

    private void asyncPerformanceTest(String testCase, Supplier<CompletableFuture<Long>> runnable) throws InterruptedException {
        System.out.println(testCase + "...");
        System.out.println("  Warming up...");
        Instant warmupEnd = Instant.now().plus(RUN_DURATION);

        Semaphore concurrencySemaphore = new Semaphore(CONCURRENT_REQUESTS);

        while (Instant.now().isBefore(warmupEnd)) {
            run(runnable, concurrencySemaphore, l -> {}, () -> {}, () -> {});
        }

        System.out.println("  Running...");
        double throughputTotal = 0;
        double latencyTotal = 0;
        for (int i = 1; i <= NUM_RUNS; ++i) {
            Instant end = Instant.now().plus(RUN_DURATION);
            AtomicLong latency = new AtomicLong(0);
            AtomicLong success = new AtomicLong(0);
            AtomicLong failures = new AtomicLong(0);

            while (Instant.now().isBefore(end)) {
                run(runnable, concurrencySemaphore, latency::addAndGet, success::incrementAndGet, failures::incrementAndGet);
            }

            double latencyMeasurement = latency.get() / (success.get() + failures.get());
            double successMeasurement = success.get() / RUN_DURATION.getSeconds();
            throughputTotal += successMeasurement;
            latencyTotal += latencyMeasurement;
            System.out.println("    Run " + i + " Throughput: ~" + successMeasurement + " ops/sec, Latency: " + latencyMeasurement + " ms (" + failures.get() + " failures)");
        }

        System.out.println("  Summary Throughput: ~" + throughputTotal / NUM_RUNS + " average ops/sec, Latency: " + latencyTotal / NUM_RUNS + " average ms");
    }

    private void run(Supplier<CompletableFuture<Long>> runnable,
                     Semaphore concurrencySemaphore,
                     Consumer<Long> latency,
                     Runnable success,
                     Runnable failure) throws InterruptedException {
        concurrencySemaphore.acquire();
        runnable.get().whenComplete((r, t) -> {
            try {
                if (t == null) {
                    latency.accept(r);
                    success.run();
                } else {
                    System.out.println(t.getMessage());
                    t.printStackTrace();
                    failure.run();
                }
            } finally {
                concurrencySemaphore.release();
            }
        });
    }

    private CompletableFuture<Long> invoke(DynamoDbAsyncClient client) {
        long start = System.currentTimeMillis();
        return client.getItem(getRequest())
                .thenApply(r -> System.currentTimeMillis() - start);
    }

    private void invoke(DynamoDbClient client) {
        client.getItem(getRequest());
    }

    private CompletableFuture<Long> invoke(S3AsyncClient client) {
        long start = System.currentTimeMillis();
        return client.getObject(r -> r.bucket(S3_BUCKET).key(S3_FILENAME), Paths.get("/tmp/out2"))
                .thenApply(r -> System.currentTimeMillis() - start);
    }

    private void invoke(S3Client client) {
        client.getObject(r -> r.bucket(S3_BUCKET).key(S3_FILENAME), Paths.get("/tmp/out2"));
    }

    private GetItemRequest getRequest() {
        return GetItemRequest.builder()
                .tableName(DDB_TABLE)
                .key(ImmutableMap.of("id", AttributeValue.builder().s("foo").build()))
                .build();
    }

    private static class PooledBuilder implements SdkAsyncHttpClient.Builder {
        private SdkAsyncHttpClient.Builder delegate;
        private final int count;

        private PooledBuilder(SdkAsyncHttpClient.Builder delegate, int count) {
            this.delegate = delegate;
            this.count = count;
        }

        @Override
        public SdkAsyncHttpClient buildWithDefaults(AttributeMap serviceDefaults) {
            return new PooledClient(IntStream.range(0, count)
                    .mapToObj(i -> delegate.buildWithDefaults(serviceDefaults))
                    .collect(toList()));
        }

        private class PooledClient implements SdkAsyncHttpClient {
            private final List<SdkAsyncHttpClient> clients;

            private PooledClient(List<SdkAsyncHttpClient> clients) {
                this.clients = clients;
            }

            @Override
            public CompletableFuture<Void> execute(AsyncExecuteRequest request) {
                int clientIndex = ThreadLocalRandom.current().nextInt(0, count);
                return clients.get(clientIndex).execute(request);
            }

            @Override
            public void close() {
                clients.forEach(c -> IoUtils.closeQuietly(c, null));
            }
        }
    }

}
