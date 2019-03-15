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

package software.amazon.awssdk.benchmark.apicall.sync;

import static software.amazon.awssdk.benchmark.utils.BenchmarkUtil.LOCAL_HTTP_URI;
import static software.amazon.awssdk.benchmark.utils.BenchmarkUtil.HTTP_PORT_NUMBER;
import static software.amazon.awssdk.benchmark.utils.BenchmarkUtil.waitForComplete;

import java.util.Collection;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import org.openjdk.jmh.annotations.Benchmark;
import org.openjdk.jmh.annotations.BenchmarkMode;
import org.openjdk.jmh.annotations.Fork;
import org.openjdk.jmh.annotations.Level;
import org.openjdk.jmh.annotations.Measurement;
import org.openjdk.jmh.annotations.Mode;
import org.openjdk.jmh.annotations.OperationsPerInvocation;
import org.openjdk.jmh.annotations.Scope;
import org.openjdk.jmh.annotations.Setup;
import org.openjdk.jmh.annotations.State;
import org.openjdk.jmh.annotations.TearDown;
import org.openjdk.jmh.annotations.Warmup;
import org.openjdk.jmh.infra.Blackhole;
import org.openjdk.jmh.profile.StackProfiler;
import org.openjdk.jmh.results.RunResult;
import org.openjdk.jmh.runner.Runner;
import org.openjdk.jmh.runner.options.Options;
import org.openjdk.jmh.runner.options.OptionsBuilder;
import software.amazon.awssdk.benchmark.utils.MockServer;
import software.amazon.awssdk.http.SdkHttpClient;
import software.amazon.awssdk.http.urlconnection.UrlConnectionHttpClient;
import software.amazon.awssdk.services.protocolrestjson.ProtocolRestJsonClient;

/**
 * Benchmarking for running with different http clients.
 */
@State(Scope.Thread)
@Warmup(iterations = 3, time = 15, timeUnit = TimeUnit.SECONDS)
@Measurement(iterations = 5, time = 10, timeUnit = TimeUnit.SECONDS)
@Fork(2) // To reduce difference between each run
@BenchmarkMode(Mode.Throughput)
public class UrlConnectionHttpClientClientBenchmark implements SdkApiCallBenchmark {

    private MockServer mockServer;
    private SdkHttpClient sdkHttpClient;
    private ProtocolRestJsonClient client;
    private ExecutorService executorService = Executors.newFixedThreadPool(50);

    @Setup(Level.Trial)
    public void setup() throws Exception {
        mockServer = new MockServer(HTTP_PORT_NUMBER);
        mockServer.start();
        sdkHttpClient = UrlConnectionHttpClient.builder().build();
        client = ProtocolRestJsonClient.builder()
                                       .endpointOverride(LOCAL_HTTP_URI)
                                       .httpClient(sdkHttpClient)
                                       .build();
    }

    @TearDown(Level.Trial)
    public void tearDown() throws Exception {
        executorService.shutdown();
        mockServer.stop();
        sdkHttpClient.close();
        client.close();
    }

    @Benchmark
    @Override
    public void sequentialApiCall(Blackhole blackhole) {
        blackhole.consume(client.allTypes());
    }

    @Benchmark
    @OperationsPerInvocation(50)
    @Override
    public void concurrentApiCall(Blackhole blackhole) {
        CountDownLatch countDownLatch = new CountDownLatch(50);
        for (int i = 0; i < 50; i++) {
            waitForComplete(blackhole, CompletableFuture.runAsync(() -> client.allTypes(), executorService), countDownLatch);
        }

        try {
            countDownLatch.await(10, TimeUnit.SECONDS);
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
    }

    public static void main(String... args) throws Exception {

        Options opt = new OptionsBuilder()
            .include(UrlConnectionHttpClientClientBenchmark.class.getSimpleName())
            .addProfiler(StackProfiler.class)
            .build();
        Collection<RunResult> run = new Runner(opt).run();
    }
}
