package software.amazon.awssdk.http.crt;

import static software.amazon.awssdk.testutils.service.AwsTestBase.CREDENTIALS_PROVIDER_CHAIN;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import software.amazon.awssdk.core.SdkBytes;
import software.amazon.awssdk.crt.CrtResource;
import software.amazon.awssdk.crt.io.ClientBootstrap;
import software.amazon.awssdk.crt.io.SocketOptions;
import software.amazon.awssdk.crt.io.TlsCipherPreference;
import software.amazon.awssdk.crt.io.TlsContext;
import software.amazon.awssdk.crt.io.TlsContextOptions;
import software.amazon.awssdk.http.async.SdkAsyncHttpClient;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.kms.KmsAsyncClient;
import software.amazon.awssdk.services.kms.model.CreateAliasRequest;
import software.amazon.awssdk.services.kms.model.CreateAliasResponse;
import software.amazon.awssdk.services.kms.model.CreateKeyRequest;
import software.amazon.awssdk.services.kms.model.CreateKeyResponse;
import software.amazon.awssdk.services.kms.model.DecryptRequest;
import software.amazon.awssdk.services.kms.model.DecryptResponse;
import software.amazon.awssdk.services.kms.model.DescribeKeyRequest;
import software.amazon.awssdk.services.kms.model.DescribeKeyResponse;
import software.amazon.awssdk.services.kms.model.EncryptRequest;
import software.amazon.awssdk.services.kms.model.EncryptResponse;
import software.amazon.awssdk.services.kms.model.GenerateDataKeyRequest;
import software.amazon.awssdk.services.kms.model.GenerateDataKeyResponse;


public class AwsCrtClientKmsTest {
    private final static String KEY_ALIAS = "alias/aws-sdk-java-v2-integ-test";
    private final static Region REGION = Region.US_EAST_1;
    private final static List<SdkAsyncHttpClient> awsCrtHttpClients = new ArrayList<>();
    private final static int DEFAULT_KEY_SIZE = 32;
    private static int NUM_REQUESTS = 250;

    List<CrtResource> crtResources = new ArrayList<>();

    private void addResource(CrtResource resource) {
        crtResources.add(resource);
    }

    @Before
    public void setup() {
        Assert.assertEquals("Expected Zero allocated AwsCrtResources", 0, CrtResource.getAllocatedNativeResourceCount());

        // Create an Http Client for each TLS Cipher Preference supported on the current platform
        for (TlsCipherPreference pref: TlsCipherPreference.values()) {
            if (!TlsContextOptions.isCipherPreferenceSupported(pref)) {
                continue;
            }

            ClientBootstrap bootstrap = new ClientBootstrap(1);
            SocketOptions socketOptions = new SocketOptions();
            TlsContext tlsContext = new TlsContext();

            addResource(bootstrap);
            addResource(socketOptions);
            addResource(tlsContext);

            SdkAsyncHttpClient awsCrtHttpClient = AwsCrtAsyncHttpClient.builder()
                    .bootstrap(bootstrap)
                    .socketOptions(socketOptions)
                    .tlsContext(tlsContext)
                    .build();

            awsCrtHttpClients.add(awsCrtHttpClient);
        }
    }

    private void closeResources() {
        for (CrtResource r: crtResources) {
            r.close();
        }
    }

    @After
    public void tearDown() {
        for (SdkAsyncHttpClient awsCrtHttpClient: awsCrtHttpClients) {
            awsCrtHttpClient.close();
        }

        closeResources();
        Assert.assertEquals("Expected Zero allocated AwsCrtResources", 0, CrtResource.getAllocatedNativeResourceCount());
    }

    private String getKeyId(KmsAsyncClient kms, String keyAlias) throws Exception {
        DescribeKeyRequest req = DescribeKeyRequest.builder().keyId(keyAlias).build();
        DescribeKeyResponse resp = kms.describeKey(req).get();
        Assert.assertEquals(200, resp.sdkHttpResponse().statusCode());
        return resp.keyMetadata().keyId();
    }

    private boolean doesKeyExist(KmsAsyncClient kms, String keyAlias) {
        try {
            DescribeKeyRequest req = DescribeKeyRequest.builder().keyId(keyAlias).build();
            DescribeKeyResponse resp = kms.describeKey(req).get();
            Assert.assertEquals(200, resp.sdkHttpResponse().statusCode());
            return resp.sdkHttpResponse().isSuccessful();
        } catch (Exception e) {
            return false;
        }
    }

    private void createKeyAlias(KmsAsyncClient kms, String keyId, String keyAlias) throws Exception {
        CreateAliasRequest req = CreateAliasRequest.builder().aliasName(keyAlias).targetKeyId(keyId).build();
        CreateAliasResponse resp = kms.createAlias(req).get();
        Assert.assertEquals(200, resp.sdkHttpResponse().statusCode());
    }

    private String createKey(KmsAsyncClient kms) throws Exception {
        CreateKeyRequest req = CreateKeyRequest.builder().build();
        CreateKeyResponse resp = kms.createKey(req).get();
        Assert.assertEquals(200, resp.sdkHttpResponse().statusCode());
        return resp.keyMetadata().keyId();
    }

    private void createKeyIfNotExists(KmsAsyncClient kms, String keyAlias) throws Exception {
        if (!doesKeyExist(kms, keyAlias)) {
            String keyId = createKey(kms);
            createKeyAlias(kms, keyId, KEY_ALIAS);
        }
    }

    private SdkBytes encrypt(KmsAsyncClient kms, String keyId, String plaintext) throws Exception {
        SdkBytes bytes = SdkBytes.fromUtf8String(plaintext);
        EncryptRequest req = EncryptRequest.builder().keyId(keyId).plaintext(bytes).build();
        EncryptResponse resp = kms.encrypt(req).get();
        Assert.assertEquals(200, resp.sdkHttpResponse().statusCode());
        return resp.ciphertextBlob();
    }

    private String decrypt(KmsAsyncClient kms, SdkBytes ciphertext) throws Exception {
        DecryptRequest req = DecryptRequest.builder().ciphertextBlob(ciphertext).build();
        DecryptResponse resp = kms.decrypt(req).get();
        Assert.assertEquals(200, resp.sdkHttpResponse().statusCode());
        return resp.plaintext().asUtf8String();
    }

    private CompletableFuture<GenerateDataKeyResponse> generateDataKey(KmsAsyncClient kms, String keyId) throws Exception {
        GenerateDataKeyRequest dataKeyRequest = GenerateDataKeyRequest.builder()
                                                    .keyId(keyId)
                                                    .numberOfBytes(DEFAULT_KEY_SIZE)
                                                    .build();
        return  kms.generateDataKey(dataKeyRequest);

    }

    private void testEncryptDecryptWithKms(KmsAsyncClient kms) throws Exception {
        createKeyIfNotExists(kms, KEY_ALIAS);
        Assert.assertTrue(doesKeyExist(kms, KEY_ALIAS));
        Assert.assertFalse(doesKeyExist(kms, "alias/does-not-exist-" + UUID.randomUUID()));

        String secret = UUID.randomUUID().toString();
        SdkBytes cipherText = encrypt(kms, KEY_ALIAS, secret);
        String plainText = decrypt(kms, cipherText);

        Assert.assertEquals(plainText, secret);
    }

//    @Test
    public void testEncryptDecryptWithKms() throws Exception {
        for (SdkAsyncHttpClient awsCrtHttpClient: awsCrtHttpClients) {
            KmsAsyncClient kms = KmsAsyncClient.builder()
                                    .region(REGION)
                                    .httpClient(awsCrtHttpClient)
                                    .credentialsProvider(CREDENTIALS_PROVIDER_CHAIN)
                                    .build();

            testEncryptDecryptWithKms(kms);

            kms.close();

        }
    }


    private void stress(KmsAsyncClient kms, String keyId) throws Exception {
        List<CompletableFuture> futures = new ArrayList<>();

        for (int i = 0; i < NUM_REQUESTS; i++) {
            CompletableFuture<GenerateDataKeyResponse> f = generateDataKey(kms, keyId);

            f.whenComplete((resp, throwable) -> {
                Assert.assertEquals(200, resp.sdkHttpResponse().statusCode());
                Assert.assertEquals(DEFAULT_KEY_SIZE, resp.plaintext().asByteArray().length);
            });
        }


        for(CompletableFuture f: futures) {
            f.join();
        }
    }


    @Test
    public void perfTest() throws Exception {
        for (SdkAsyncHttpClient awsCrtHttpClient: awsCrtHttpClients) {

            KmsAsyncClient kms = KmsAsyncClient.builder()
                    .region(REGION)
                    .httpClient(awsCrtHttpClient)
                    .credentialsProvider(CREDENTIALS_PROVIDER_CHAIN)
                    .build();
            createKeyIfNotExists(kms, KEY_ALIAS);
            String keyId = getKeyId(kms, KEY_ALIAS);

            stress(kms, keyId);

            kms.close();
        }
    }

}
