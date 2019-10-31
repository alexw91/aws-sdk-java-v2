package software.amazon.awssdk.http.crt;

import static software.amazon.awssdk.testutils.service.AwsTestBase.CREDENTIALS_PROVIDER_CHAIN;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.lang.management.ManagementFactory;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;

import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import software.amazon.awssdk.awscore.AwsRequestOverrideConfiguration;
import software.amazon.awssdk.core.SdkBytes;
import software.amazon.awssdk.crt.CRT;
import software.amazon.awssdk.crt.CrtResource;
import software.amazon.awssdk.crt.Log;
import software.amazon.awssdk.crt.io.TlsCipherPreference;
import software.amazon.awssdk.crt.io.TlsContextOptions;
import software.amazon.awssdk.http.async.SdkAsyncHttpClient;
import software.amazon.awssdk.http.crt.internal.AwsCrtAsyncHttpStreamAdapter;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.kms.KmsAsyncClient;
import software.amazon.awssdk.services.kms.model.*;
import software.amazon.awssdk.utils.Logger;


public class AwsCrtClientKmsTest {
    private static final Logger log = Logger.loggerFor(AwsCrtClientKmsTest.class);
    private static String KEY_ALIAS = "alias/aws-sdk-java-v2-integ-test";
    private static final int NUM_RANDOM_BYTES = 32;
    private static Region REGION = Region.US_EAST_1;
    private static List<SdkAsyncHttpClient> awsCrtHttpClients = new ArrayList<>();

    @Before
    public void setup() {
        CrtResource.waitForNoResources();

        // Create an Http Client for each TLS Cipher Preference supported on the current platform
        for (TlsCipherPreference pref: TlsCipherPreference.values()) {
            if (!TlsContextOptions.isCipherPreferenceSupported(pref)) {
                continue;
            }

            SdkAsyncHttpClient awsCrtHttpClient = AwsCrtAsyncHttpClient.builder()
                    .eventLoopSize(1)
                    .tlsCipherPreference(pref)
                    .build();

            awsCrtHttpClients.add(awsCrtHttpClient);
        }
    }


    @After
    public void tearDown() {
        CrtResource.waitForNoResources();
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

    public Consumer<AwsRequestOverrideConfiguration.Builder> requestCloseConnection() {
        /* See https://tools.ietf.org/html/rfc2616#section-14.10 which specifies the "close" header to signal the
         * connection will be closed after the server responds. This is more efficient that deleting the entire SDK and
         * HTTP client for every transaction.
         */
        return b -> b.putHeader("Connection", "close");
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

    private AtomicLong numCompletedTransactions = new AtomicLong(0);
    private AtomicLong numFailed = new AtomicLong(0);

    private ConcurrentLinkedQueue<Exception> queue = new ConcurrentLinkedQueue<>();


    private void genDataKey(KmsAsyncClient kms) {
        try {
            GenerateDataKeyRequest dataKeyRequest = GenerateDataKeyRequest.builder()
                    .keyId(KEY_ALIAS)
                    .numberOfBytes(NUM_RANDOM_BYTES)
                    .overrideConfiguration(requestCloseConnection())
                    .build();
            kms.generateDataKey(dataKeyRequest).get(10, TimeUnit.SECONDS);
            numCompletedTransactions.addAndGet(1);
        } catch (Exception e) {
            queue.add(e);
//            log.error(() -> e.getMessage());
//            e.printStackTrace();
            numFailed.addAndGet(1);
        }
    }

    private static String getCurrentTime() {
        return ZonedDateTime.now(ZoneOffset.UTC).format(DateTimeFormatter.ISO_INSTANT);
    }

    private static void getLinuxMemInfo() throws Exception {
        final String meminfoPath = "/proc/meminfo";

        FileReader reader = new FileReader(new File(meminfoPath));
        BufferedReader meminfo = new BufferedReader(reader);

        String line = null;
        while ((line = meminfo.readLine()) != null) {
            if (line.contains("Mem")) {
                System.out.println(line);
            }
        }
    }

    @Test
    public void testGenerateDataKey() throws Exception {
        Log.initLoggingToFile(Log.LogLevel.Trace, "/home/ANT.AMAZON.COM/aweibel/workspace/github/aws-sdk-java-v2/crt-logs-" + getCurrentTime() + ".txt");

        SdkAsyncHttpClient awsCrtHttpClient = AwsCrtAsyncHttpClient.builder()
                .eventLoopSize(1)
                .tlsCipherPreference(TlsCipherPreference.TLS_CIPHER_KMS_PQ_TLSv1_0_2019_06)
                .build();

        KmsAsyncClient kms = KmsAsyncClient.builder()
                .region(REGION)
                .httpClient(awsCrtHttpClient)
                .credentialsProvider(CREDENTIALS_PROVIDER_CHAIN)
                .build();

        createKeyIfNotExists(kms, KEY_ALIAS);

        ExecutorService threadPool = Executors.newFixedThreadPool(32);

        int numTotalTransactions = 100;
        int requestIntervalMillis = 50;

        int logIntervalMillis= 5000;
        long lastPrintTime = 0;

        while (true) {

            if (numCompletedTransactions.get() == numTotalTransactions) {
                break;
            }

//            System.out.println("Scheduling genDataKey()");
//            for(int i = 0; i < 10; i++) {
//                threadPool.execute(() -> { try { genDataKey(kms); } catch (Exception e) { /*Do Nothing*/ } });
//            }
//

            genDataKey(kms);
            Thread.sleep(requestIntervalMillis);


            long timeSinceLastLog = (System.currentTimeMillis() - lastPrintTime);

            if (timeSinceLastLog > (logIntervalMillis)) {
                lastPrintTime = System.currentTimeMillis();
                System.out.println("\nCurr Time: " + getCurrentTime());
                System.out.println("PID:" + ManagementFactory.getRuntimeMXBean().getName());
                System.out.println("CRT.nativeMemory: " + CRT.nativeMemory());
                System.out.println("numCompletedTransactions: " + numCompletedTransactions.get());
                System.out.println("numFailedTransactions: " + numFailed.get());
                getLinuxMemInfo();
            }

            if (queue.size() > 0) {
                Exception e = queue.poll();

                System.out.println("\nException: " + e.getMessage());
                e.printStackTrace();
            }
        }

        kms.close();
        awsCrtHttpClient.close();

        AtomicBoolean zeroCrtResources = new AtomicBoolean(false);
        AtomicReference<Exception> awaitException = new AtomicReference<>(null);
        CrtResource.waitForNoResources();

        System.out.println("CRT.nativeMemory: " + CRT.nativeMemory());

        Thread.sleep(1000 * 1000);
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
            awsCrtHttpClient.close();
        }
    }
}
