package software.amazon.awssdk.http.crt;

import static software.amazon.awssdk.testutils.service.AwsTestBase.CREDENTIALS_PROVIDER_CHAIN;

import java.util.ArrayList;
import java.util.List;
import org.testng.annotations.Test;
import software.amazon.awssdk.crt.CrtResource;
import software.amazon.awssdk.crt.io.ClientBootstrap;
import software.amazon.awssdk.crt.io.SocketOptions;
import software.amazon.awssdk.crt.io.TlsCipherPreference;
import software.amazon.awssdk.crt.io.TlsContext;
import software.amazon.awssdk.crt.io.TlsContextOptions;
import software.amazon.awssdk.http.async.SdkAsyncHttpClient;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.kms.KmsAsyncClient;

public class KmsStressTest {
    private static Region REGION = Region.US_EAST_1;
    List<CrtResource> crtResources = new ArrayList<>();



//    private void generateDataKet(KmsAsyncClient kms) {
//
//    }
//
//
//    @Test
//    public void stressTest() {
//
//        ClientBootstrap bootstrap = new ClientBootstrap(1);
//        SocketOptions socketOptions = new SocketOptions();
//        TlsCipherPreference preferenceEnum = TlsCipherPreference.TLS_CIPHER_SYSTEM_DEFAULT;
//
//        TlsContext tlsContext = new TlsContext(new TlsContextOptions().withCipherPreference(preferenceEnum));
//
//        crtResources.add(bootstrap);
//        crtResources.add(socketOptions);
//        crtResources.add(tlsContext);
//
//        SdkAsyncHttpClient awsCrtHttpClient = AwsCrtAsyncHttpClient.builder()
//                .bootstrap(bootstrap)
//                .socketOptions(socketOptions)
//                .tlsContext(tlsContext)
//                .build();
//
//        KmsAsyncClient kms = KmsAsyncClient.builder()
//                .region(REGION)
//                .httpClient(awsCrtHttpClient)
//                .credentialsProvider(CREDENTIALS_PROVIDER_CHAIN)
//                .build();
//
//
//    }
}
