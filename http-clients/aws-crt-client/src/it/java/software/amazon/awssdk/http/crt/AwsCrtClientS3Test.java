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

import static org.assertj.core.api.Assertions.assertThat;

import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import software.amazon.awssdk.core.async.AsyncResponseTransformer;
import software.amazon.awssdk.crt.io.ClientBootstrap;
import software.amazon.awssdk.crt.io.SocketOptions;
import software.amazon.awssdk.crt.io.TlsContext;
import software.amazon.awssdk.http.async.SdkAsyncHttpClient;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3AsyncClient;
import software.amazon.awssdk.services.s3.model.GetObjectRequest;


public class AwsCrtClientS3Test {

    /**
     * The name of the bucket created, used, and deleted by these tests.
     */
    private static String BUCKET_NAME = "aws-crt-test-stuff";

    private static String KEY = "http_test_doc.txt";

    private static String FILE_SHA256 = "C7FDB5314B9742467B16BD5EA2F8012190B5E2C44A005F7984F89AAB58219534";

    private static Region REGION = Region.US_WEST_2;

    private static SdkAsyncHttpClient client;

    private static S3AsyncClient s3;


    @Before
    public void setup() {
        client = AwsCrtAsyncHttpClient.builder()
                    .bootstrap(new ClientBootstrap(1))
                    .socketOptions(new SocketOptions())
                    .tlsContext(new TlsContext())
                    .build();

        s3 = S3AsyncClient.builder()
                  .region(REGION)
                  .httpClient(client)
                  .build();
    }

    @After
    public void tearDown() {
        client.close();
        s3.close();
    }

    public static String byteArrayToHex(byte[] input) {
        StringBuilder output = new StringBuilder(input.length * 2);
        for (byte b: input) {
            output.append(String.format("%02X", b));
        }
        return output.toString();
    }

    private String calculateBodyHash(byte[] bodyBuffer) throws NoSuchAlgorithmException {
        MessageDigest digest = MessageDigest.getInstance("SHA-256");
        digest.update(bodyBuffer);
        return byteArrayToHex(digest.digest());
    }

    @Test
    public void testDownloadFromS3() throws NoSuchAlgorithmException {
        GetObjectRequest s3Request = GetObjectRequest.builder()
                .bucket(BUCKET_NAME)
                .key(KEY)
                .build();

        byte[] responseBody = s3.getObject(s3Request, AsyncResponseTransformer.toBytes()).join().asByteArray();

        assertThat(calculateBodyHash(responseBody)).isEqualTo(FILE_SHA256);
    }
}
