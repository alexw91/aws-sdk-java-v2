/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
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

package software.amazon.awssdk.core.http;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static software.amazon.awssdk.core.internal.useragent.UserAgentConstant.HTTP;
import static software.amazon.awssdk.core.internal.useragent.UserAgentConstant.IO;
import static software.amazon.awssdk.core.internal.util.ResponseHandlerTestUtils.combinedSyncResponseHandler;

import java.io.IOException;
import java.util.concurrent.ExecutorService;

import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;

import software.amazon.awssdk.core.ClientType;
import software.amazon.awssdk.core.client.config.SdkAdvancedAsyncClientOption;
import software.amazon.awssdk.core.client.config.SdkAdvancedClientOption;
import software.amazon.awssdk.core.client.config.SdkClientConfiguration;
import software.amazon.awssdk.core.client.config.SdkClientOption;
import software.amazon.awssdk.core.exception.SdkClientException;
import software.amazon.awssdk.core.internal.http.AmazonSyncHttpClient;
import software.amazon.awssdk.core.internal.http.timers.ClientExecutionAndRequestTimerTestUtils;
import software.amazon.awssdk.core.internal.useragent.SdkClientUserAgentProperties;
import software.amazon.awssdk.core.internal.useragent.SdkUserAgentBuilder;
import software.amazon.awssdk.core.util.SystemUserAgent;
import software.amazon.awssdk.http.ExecutableHttpRequest;
import software.amazon.awssdk.http.HttpExecuteRequest;
import software.amazon.awssdk.http.HttpExecuteResponse;
import software.amazon.awssdk.http.SdkHttpClient;
import software.amazon.awssdk.http.SdkHttpResponse;
import software.amazon.awssdk.utils.StringUtils;
import software.amazon.awssdk.utils.http.SdkHttpUtils;
import utils.HttpTestUtils;
import utils.ValidSdkObjects;

@RunWith(MockitoJUnitRunner.class)
public class AmazonHttpClientTest {

    @Mock
    private SdkHttpClient sdkHttpClient;

    @Mock
    private ExecutableHttpRequest abortableCallable;

    @Mock
    private ExecutorService executor;

    private AmazonSyncHttpClient client;

    @Before
    public void setUp() throws Exception {
        client = HttpTestUtils.testClientBuilder().httpClient(sdkHttpClient).build();
        when(sdkHttpClient.prepareRequest(any())).thenReturn(abortableCallable);
        when(sdkHttpClient.clientName()).thenReturn("UNKNOWN");
        stubSuccessfulResponse();
    }

    @Test
    public void testRetryIoExceptionFromExecute() throws Exception {
        IOException ioException = new IOException("BOOM");

        when(abortableCallable.call()).thenThrow(ioException);

        try {
            client.requestExecutionBuilder()
                    .request(ValidSdkObjects.sdkHttpFullRequest().build())
                    .originalRequest(NoopTestRequest.builder().build())
                    .executionContext(executionContext())
                    .execute(combinedSyncResponseHandler(null, null));
            Assert.fail("No exception when request repeatedly fails!");

        } catch (SdkClientException e) {
            Assert.assertSame(ioException, e.getCause());
        }

        // Verify that we called execute 4 times.
        verify(sdkHttpClient, times(4)).prepareRequest(any());
    }

    @Test
    public void testRetryIoExceptionFromHandler() throws Exception {
        IOException exception = new IOException("BOOM");

        HttpResponseHandler<?> mockHandler = mock(HttpResponseHandler.class);
        when(mockHandler.handle(any(), any())).thenThrow(exception);

        try {
            client.requestExecutionBuilder()
                    .request(ValidSdkObjects.sdkHttpFullRequest().build())
                    .originalRequest(NoopTestRequest.builder().build())
                    .executionContext(executionContext())
                    .execute(combinedSyncResponseHandler(mockHandler, null));
            Assert.fail("No exception when request repeatedly fails!");

        } catch (SdkClientException e) {
            Assert.assertSame(exception, e.getCause());
        }

        // Verify that we called execute 4 times.
        verify(mockHandler, times(4)).handle(any(), any());
    }


    @Test
    public void testUserAgentPrefixAndSuffixAreAdded() {
        String prefix = "somePrefix";
        String suffix = "someSuffix-blah-blah";

        HttpResponseHandler<?> handler = mock(HttpResponseHandler.class);

        SdkClientUserAgentProperties userAgentProperties = new SdkClientUserAgentProperties();
        userAgentProperties.putProperty(IO, ClientType.SYNC.name());
        userAgentProperties.putProperty(HTTP, SdkHttpUtils.urlEncode(sdkHttpClient.clientName()));
        String clientUserAgent = SdkUserAgentBuilder.buildClientUserAgentString(SystemUserAgent.getOrCreate(),
                                                                               userAgentProperties);

        SdkClientConfiguration config = HttpTestUtils.testClientConfiguration().toBuilder()
                                                     .option(SdkClientOption.CLIENT_USER_AGENT, clientUserAgent)
                                                     .option(SdkAdvancedClientOption.USER_AGENT_PREFIX, prefix)
                                                     .option(SdkAdvancedClientOption.USER_AGENT_SUFFIX, suffix)
                                                     .option(SdkClientOption.SYNC_HTTP_CLIENT, sdkHttpClient)
                                                     .build();
        AmazonSyncHttpClient client = new AmazonSyncHttpClient(config);

        client.requestExecutionBuilder()
              .request(ValidSdkObjects.sdkHttpFullRequest().build())
              .originalRequest(NoopTestRequest.builder().build())
              .executionContext(executionContext())
              .execute(combinedSyncResponseHandler(handler, null));

        ArgumentCaptor<HttpExecuteRequest> httpRequestCaptor = ArgumentCaptor.forClass(HttpExecuteRequest.class);
        verify(sdkHttpClient).prepareRequest(httpRequestCaptor.capture());

        String userAgent = httpRequestCaptor.getValue().httpRequest().firstMatchingHeader("User-Agent")
                                            .orElseThrow(() -> new AssertionError("User-Agent header was not found"));

        Assert.assertTrue(userAgent.startsWith(prefix));
        Assert.assertTrue(userAgent.endsWith(suffix));
    }

    @Test
    public void testUserAgentContainsHttpClientInfo() {
        HttpResponseHandler<?> handler = mock(HttpResponseHandler.class);

        SdkClientUserAgentProperties userAgentProperties = new SdkClientUserAgentProperties();
        userAgentProperties.putProperty(IO, StringUtils.lowerCase(ClientType.SYNC.name()));
        userAgentProperties.putProperty(HTTP, SdkHttpUtils.urlEncode(sdkHttpClient.clientName()));
        String clientUserAgent = SdkUserAgentBuilder.buildClientUserAgentString(SystemUserAgent.getOrCreate(),
                                                                                userAgentProperties);

        SdkClientConfiguration config = HttpTestUtils.testClientConfiguration().toBuilder()
                                                     .option(SdkClientOption.CLIENT_USER_AGENT, clientUserAgent)
                                                     .option(SdkClientOption.SYNC_HTTP_CLIENT, sdkHttpClient)
                                                     .option(SdkClientOption.CLIENT_TYPE, ClientType.SYNC)
                                                     .build();
        AmazonSyncHttpClient client = new AmazonSyncHttpClient(config);

        client.requestExecutionBuilder()
              .request(ValidSdkObjects.sdkHttpFullRequest().build())
              .originalRequest(NoopTestRequest.builder().build())
              .executionContext(executionContext())
              .execute(combinedSyncResponseHandler(handler, null));

        ArgumentCaptor<HttpExecuteRequest> httpRequestCaptor = ArgumentCaptor.forClass(HttpExecuteRequest.class);
        verify(sdkHttpClient).prepareRequest(httpRequestCaptor.capture());

        String userAgent = httpRequestCaptor.getValue().httpRequest().firstMatchingHeader("User-Agent")
                                            .orElseThrow(() -> new AssertionError("User-Agent header was not found"));

        Assert.assertTrue(userAgent.contains("io#sync"));
        Assert.assertTrue(userAgent.contains("http#UNKNOWN"));
    }

    @Test
    public void closeClient_shouldCloseDependencies() {
        SdkClientConfiguration config = HttpTestUtils.testClientConfiguration()
                                                     .toBuilder()
                                                     .option(SdkAdvancedAsyncClientOption.FUTURE_COMPLETION_EXECUTOR, executor)
                                                     .option(SdkClientOption.SYNC_HTTP_CLIENT, sdkHttpClient)
                                                     .build();

        AmazonSyncHttpClient client = new AmazonSyncHttpClient(config);
        client.close();
        verify(sdkHttpClient).close();
        verify(executor).shutdown();
    }

    private ExecutionContext executionContext() {
        return ClientExecutionAndRequestTimerTestUtils.executionContext(null);
    }

    private void stubSuccessfulResponse() throws Exception {
        when(abortableCallable.call()).thenReturn(HttpExecuteResponse.builder().response(SdkHttpResponse.builder()
                                                                                                        .statusCode(200)
                                                                                                        .build())
                                                                     .build());
    }
}
