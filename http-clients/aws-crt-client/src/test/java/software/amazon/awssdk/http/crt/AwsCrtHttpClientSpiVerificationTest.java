package software.amazon.awssdk.http.crt;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.any;
import static com.github.tomakehurst.wiremock.client.WireMock.stubFor;
import static com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo;
import static com.github.tomakehurst.wiremock.core.WireMockConfiguration.wireMockConfig;
import static java.nio.charset.StandardCharsets.UTF_8;
import static java.util.Collections.emptyMap;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.github.tomakehurst.wiremock.http.Fault;
import com.github.tomakehurst.wiremock.junit.WireMockRule;
import java.net.URI;
import java.nio.ByteBuffer;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.reactivestreams.Publisher;
import org.reactivestreams.Subscriber;
import org.reactivestreams.Subscription;
import software.amazon.awssdk.crt.io.ClientBootstrap;
import software.amazon.awssdk.crt.io.SocketOptions;
import software.amazon.awssdk.crt.io.TlsContext;
import software.amazon.awssdk.http.SdkHttpFullRequest;
import software.amazon.awssdk.http.SdkHttpMethod;
import software.amazon.awssdk.http.SdkHttpRequest;
import software.amazon.awssdk.http.SdkHttpResponse;
import software.amazon.awssdk.http.async.AsyncExecuteRequest;
import software.amazon.awssdk.http.async.SdkAsyncHttpClient;
import software.amazon.awssdk.http.async.SdkAsyncHttpResponseHandler;

public class AwsCrtHttpClientSpiVerificationTest {
    @Rule
    public WireMockRule mockServer = new WireMockRule(wireMockConfig()
            .dynamicPort()
            .dynamicHttpsPort());

    private SdkAsyncHttpClient client;

    @Before
    public void setup() {
        client = AwsCrtAsyncHttpClient.builder()
                    .bootstrap(new ClientBootstrap(1))
                    .socketOptions(new SocketOptions())
                    .tlsContext(new TlsContext())
                    .build();
    }

    @After
    public void tearDown() {
        client.close();
    }


    @Test
    public void signalsErrorViaOnErrorAndFuture() throws InterruptedException, ExecutionException, TimeoutException {
        stubFor(any(urlEqualTo("/")).willReturn(aResponse().withFault(Fault.RANDOM_DATA_THEN_CLOSE)));

        CompletableFuture<Boolean> errorSignaled = new CompletableFuture<>();

        SdkAsyncHttpResponseHandler handler = new TestResponseHandler() {
            @Override
            public void onError(Throwable error) {
                errorSignaled.complete(true);
            }
        };

        SdkHttpRequest request = createRequest(URI.create("http://localhost:" + mockServer.port()));

        CompletableFuture<Void> executeFuture = client.execute(AsyncExecuteRequest.builder()
                .request(request)
                .responseHandler(handler)
                .requestContentPublisher(new EmptyPublisher())
                .build());

        assertThat(errorSignaled.get(1, TimeUnit.SECONDS)).isTrue();
        assertThatThrownBy(executeFuture::join).hasCauseInstanceOf(Exception.class);

    }

    @Test
    public void callsOnStreamForEmptyResponseContent() throws Exception {
        stubFor(any(urlEqualTo("/")).willReturn(aResponse().withStatus(204).withHeader("foo", "bar")));

        CompletableFuture<Boolean> streamReceived = new CompletableFuture<>();

        SdkAsyncHttpResponseHandler handler = new TestResponseHandler() {
            @Override
            public void onStream(Publisher<ByteBuffer> stream) {
                super.onStream(stream);
                streamReceived.complete(true);
            }
        };

        SdkHttpRequest request = createRequest(URI.create("http://localhost:" + mockServer.port()));

        client.execute(AsyncExecuteRequest.builder()
                .request(request)
                .responseHandler(handler)
                .requestContentPublisher(new EmptyPublisher())
                .build());

        assertThat(streamReceived.get(1, TimeUnit.SECONDS)).isTrue();
    }

    private SdkHttpFullRequest createRequest(URI endpoint) {
        return createRequest(endpoint, "/", null, SdkHttpMethod.GET, emptyMap());
    }

    private SdkHttpFullRequest createRequest(URI endpoint,
                                             String resourcePath,
                                             String body,
                                             SdkHttpMethod method,
                                             Map<String, String> params) {

        String contentLength = body == null ? null : String.valueOf(body.getBytes(UTF_8).length);
        return SdkHttpFullRequest.builder()
                .uri(endpoint)
                .method(method)
                .encodedPath(resourcePath)
                .applyMutation(b -> params.forEach(b::putRawQueryParameter))
                .applyMutation(b -> {
                    b.putHeader("Host", endpoint.getHost());
                    if (contentLength != null) {
                        b.putHeader("Content-Length", contentLength);
                    }
                }).build();
    }

    private static class TestResponseHandler implements SdkAsyncHttpResponseHandler {
        @Override
        public void onHeaders(SdkHttpResponse headers) {
        }

        @Override
        public void onStream(Publisher<ByteBuffer> stream) {
            stream.subscribe(new DrainingSubscriber<>());
        }

        @Override
        public void onError(Throwable error) {
        }
    }

    private static class DrainingSubscriber<T> implements Subscriber<T> {
        private Subscription subscription;

        @Override
        public void onSubscribe(Subscription subscription) {
            this.subscription = subscription;
            this.subscription.request(Long.MAX_VALUE);
        }

        @Override
        public void onNext(T t) {
            this.subscription.request(1);
        }

        @Override
        public void onError(Throwable throwable) {
        }

        @Override
        public void onComplete() {
        }
    }
}
