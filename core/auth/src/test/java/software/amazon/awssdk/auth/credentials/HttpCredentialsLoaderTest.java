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

package software.amazon.awssdk.auth.credentials;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.equalTo;
import static com.github.tomakehurst.wiremock.client.WireMock.get;
import static com.github.tomakehurst.wiremock.client.WireMock.stubFor;
import static com.github.tomakehurst.wiremock.client.WireMock.urlPathEqualTo;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;

import com.github.tomakehurst.wiremock.junit.WireMockRule;
import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.util.Date;
import org.junit.BeforeClass;
import org.junit.ClassRule;
import org.junit.Test;
import software.amazon.awssdk.auth.credentials.internal.HttpCredentialsLoader;
import software.amazon.awssdk.auth.credentials.internal.StaticResourcesEndpointProvider;
import software.amazon.awssdk.core.exception.SdkClientException;
import software.amazon.awssdk.core.util.SdkUserAgent;
import software.amazon.awssdk.regions.util.ResourcesEndpointProvider;
import software.amazon.awssdk.utils.DateUtils;
import software.amazon.awssdk.utils.IoUtils;

public class HttpCredentialsLoaderTest {
    @ClassRule
    public static WireMockRule mockServer = new WireMockRule(0);
    /** One minute (in milliseconds) */
    private static final long ONE_MINUTE = 1000L * 60;
    /** Environment variable name for the AWS ECS Container credentials path. */
    private static final String CREDENTIALS_PATH = "/dummy/credentials/path";
    private static final String PROVIDER_NAME = "HttpCredentialsProvider";
    private static String successResponse;

    private static String successResponseWithInvalidBody;

    @BeforeClass
    public static void setup() throws IOException {
        try (InputStream successInputStream = HttpCredentialsLoaderTest.class.getResourceAsStream
            ("/resources/wiremock/successResponse.json");
             InputStream responseWithInvalidBodyInputStream = HttpCredentialsLoaderTest.class.getResourceAsStream
                 ("/resources/wiremock/successResponseWithInvalidBody.json")) {
            successResponse = IoUtils.toUtf8String(successInputStream);
            successResponseWithInvalidBody = IoUtils.toUtf8String(responseWithInvalidBodyInputStream);
        }
    }

    /**
     * Test that loadCredentials returns proper credentials when response from client is in proper Json format.
     */
    @Test
    public void testLoadCredentialsParsesJsonResponseProperly() {
        stubForSuccessResponseWithCustomBody(successResponse);

        HttpCredentialsLoader credentialsProvider = HttpCredentialsLoader.create(PROVIDER_NAME);
        AwsSessionCredentials credentials = (AwsSessionCredentials) credentialsProvider.loadCredentials(testEndpointProvider())
                                                                                       .getAwsCredentials();

        assertThat(credentials.accessKeyId()).isEqualTo("ACCESS_KEY_ID");
        assertThat(credentials.secretAccessKey()).isEqualTo("SECRET_ACCESS_KEY");
        assertThat(credentials.sessionToken()).isEqualTo("TOKEN_TOKEN_TOKEN");
    }

    /**
     * Test that when credentials are null and response from client does not have access key/secret key,
     * throws RuntimeException.
     */
    @Test
    public void testLoadCredentialsThrowsAceWhenClientResponseDontHaveKeys() {
        // Stub for success response but without keys in the response body
        stubForSuccessResponseWithCustomBody(successResponseWithInvalidBody);

        HttpCredentialsLoader credentialsProvider = HttpCredentialsLoader.create(PROVIDER_NAME);

        assertThatExceptionOfType(SdkClientException.class).isThrownBy(() -> credentialsProvider.loadCredentials(testEndpointProvider()))
                                                           .withMessage("Failed to load credentials from metadata service.");
    }

    /**
     * Tests how the credentials provider behaves when the
     * server is not running.
     */
    @Test
    public void testNoMetadataService() throws Exception {
        stubForErrorResponse();

        HttpCredentialsLoader credentialsProvider = HttpCredentialsLoader.create(PROVIDER_NAME);

        // When there are no credentials, the provider should throw an exception if we can't connect
        assertThatExceptionOfType(SdkClientException.class).isThrownBy(() -> credentialsProvider.loadCredentials(testEndpointProvider()));

        // When there are valid credentials (but need to be refreshed) and the endpoint returns 404 status,
        // the provider should throw an exception.
        stubForSuccessResonseWithCustomExpirationDate(new Date(System.currentTimeMillis() + ONE_MINUTE * 4));
        credentialsProvider.loadCredentials(testEndpointProvider()); // loads the credentials that will be expired soon

        stubForErrorResponse();  // Behaves as if server is unavailable.
        assertThatExceptionOfType(SdkClientException.class).isThrownBy(() -> credentialsProvider.loadCredentials(testEndpointProvider()));
    }

    private void stubForSuccessResponseWithCustomBody(String body) {
        stubFor(
            get(urlPathEqualTo(CREDENTIALS_PATH))
                .withHeader("User-Agent", equalTo(SdkUserAgent.create().userAgent()))
                .willReturn(aResponse()
                                .withStatus(200)
                                .withHeader("Content-Type", "application/json")
                                .withHeader("charset", "utf-8")
                                .withBody(body)));
    }

    private void stubForSuccessResonseWithCustomExpirationDate(Date expiration) {
        stubForSuccessResponseWithCustomBody("{\"AccessKeyId\":\"ACCESS_KEY_ID\",\"SecretAccessKey\":\"SECRET_ACCESS_KEY\","
                                             + "\"Expiration\":\"" + DateUtils.formatIso8601Date(expiration.toInstant())
                                             + "\"}");
    }

    private void stubForErrorResponse() {
        stubFor(
            get(urlPathEqualTo(CREDENTIALS_PATH))
                .willReturn(aResponse()
                                .withStatus(404)
                                .withHeader("Content-Type", "application/json")
                                .withHeader("charset", "utf-8")
                                .withBody("{\"code\":\"404 Not Found\",\"message\":\"DetailedErrorMessage\"}")));
    }


    private ResourcesEndpointProvider testEndpointProvider() {
        return  StaticResourcesEndpointProvider.builder()
                                               .endpoint(URI.create("http://localhost:" + mockServer.port() + CREDENTIALS_PATH))
                                               .build();
    }
}
