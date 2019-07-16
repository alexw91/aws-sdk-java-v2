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

import static org.apache.commons.codec.digest.DigestUtils.sha256Hex;
import static org.assertj.core.api.Assertions.assertThat;

import java.util.concurrent.TimeUnit;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import software.amazon.awssdk.auth.credentials.AnonymousCredentialsProvider;
import software.amazon.awssdk.core.ResponseInputStream;
import software.amazon.awssdk.core.async.AsyncResponseTransformer;
import software.amazon.awssdk.crt.io.ClientBootstrap;
import software.amazon.awssdk.crt.io.SocketOptions;
import software.amazon.awssdk.crt.io.TlsContext;
import software.amazon.awssdk.http.SdkHttpConfigurationOption;
import software.amazon.awssdk.http.apache.ApacheHttpClient;
import software.amazon.awssdk.http.async.SdkAsyncHttpClient;
import software.amazon.awssdk.http.nio.netty.NettyNioAsyncHttpClient;
import software.amazon.awssdk.http.urlconnection.UrlConnectionHttpClient;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3AsyncClient;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.GetObjectRequest;
import software.amazon.awssdk.services.s3.model.GetObjectResponse;
import software.amazon.awssdk.utils.AttributeMap;
import software.amazon.awssdk.utils.Logger;


public class AwsCrtClientS3Test {
    private static final Logger log = Logger.loggerFor(AwsCrtClientS3Test.class);
    /**
     * The name of the bucket created, used, and deleted by these tests.
     */
    private static String BUCKET_NAME = "s2n-public-test-dependencies";

    private static String KEY = "random_128MB.data";

    private static String FILE_SHA256 = "C7FDB5314B9742467B16BD5EA2F8012190B5E2C44A005F7984F89AAB58219534";

    private static Region REGION = Region.US_EAST_1;

    private static SdkAsyncHttpClient crtClient;
    private static SdkAsyncHttpClient nettyClient;

    private static S3AsyncClient s3CrtClient;
    private static S3AsyncClient s3NettyClient;
    private static S3AsyncClient defaultS3AsyncClient;


//    private static S3Client defaultS3Client;
    private static S3Client s3ApacheClient;
    private static S3Client urlS3Client;


    @Before
    public void setup() {

        crtClient = AwsCrtAsyncHttpClient.builder()
                    .bootstrap(new ClientBootstrap(1))
                    .socketOptions(new SocketOptions())
                    .tlsContext(new TlsContext())
                    .build();

        nettyClient = NettyNioAsyncHttpClient.builder()
                        .buildWithDefaults(AttributeMap.builder().put(SdkHttpConfigurationOption.TRUST_ALL_CERTIFICATES, true)
                        .build());

        s3CrtClient = S3AsyncClient.builder()
                  .region(REGION)
                  .httpClient(crtClient)
                  .credentialsProvider(AnonymousCredentialsProvider.create()) // File is publicly readable
                  .build();

        s3NettyClient = S3AsyncClient.builder()
                .region(REGION)
                .httpClient(nettyClient)
                .credentialsProvider(AnonymousCredentialsProvider.create()) // File is publicly readable
                .build();

        defaultS3AsyncClient = S3AsyncClient.create();
//        defaultS3Client = S3Client.create();

        s3ApacheClient = S3Client.builder()
                            .httpClientBuilder(ApacheHttpClient.builder())
                            .build();

        urlS3Client = S3Client.builder()
                .httpClientBuilder(UrlConnectionHttpClient.builder())
                .build();
    }

    @After
    public void tearDown() {
        crtClient.close();
        s3CrtClient.close();
    }

    @Test
    public void testDownloadFromS3Async() throws Exception {
        for (int i = 0; i < 10; i++) {
            testDownloadFromS3Async("NettyNioAsyncHttpClient", s3NettyClient);
            testDownloadFromS3Async("AwsCrtAsyncHttpClient  ", s3CrtClient);
            testDownloadFromS3Async("Default S3AsyncClient  ", defaultS3AsyncClient);
            //testDownloadFromS3(     "Default S3Client       ", defaultS3Client);
            testDownloadFromS3("ApacheHttpClient       ", s3ApacheClient);
            testDownloadFromS3("UrlConnectionHttpClient", urlS3Client);
        }

    }

    public void testDownloadFromS3Async(String client, S3AsyncClient s3) throws Exception {
        GetObjectRequest s3Request = GetObjectRequest.builder()
                .bucket(BUCKET_NAME)
                .key(KEY)
                .build();

        long start = System.currentTimeMillis();
        byte[] responseBody = s3.getObject(s3Request, AsyncResponseTransformer.toBytes()).get(60, TimeUnit.SECONDS).asByteArray();
        long end = System.currentTimeMillis();

        System.out.println(client + " Millis: " + (end - start));
        System.out.println(client + " Size: " + responseBody.length);

//        assertThat(sha256Hex(responseBody).toUpperCase()).isEqualTo(FILE_SHA256);
    }

    public void testDownloadFromS3(String client, S3Client s3) throws Exception {
        GetObjectRequest s3Request = GetObjectRequest.builder()
                .bucket(BUCKET_NAME)
                .key(KEY)
                .build();

        byte[] copy = new byte[129 * 1024 * 1024];
        int pos = 0;

        long start = System.currentTimeMillis();
        ResponseInputStream<GetObjectResponse> resp = s3.getObject(s3Request);

        int remaining = resp.response().contentLength().intValue();
        while (remaining > 0) {
            int amntRead = resp.read(copy, pos, remaining);
            pos += amntRead;
            remaining -= amntRead;
        }
        long end = System.currentTimeMillis();


        System.out.println(client + " Millis: " + (end - start));
        System.out.println(client + " Size: " + resp.response().contentLength());
    }
}
